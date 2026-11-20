package main

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"regexp"
	"sort"
	"sync"
	"time"

	"github.com/coder/websocket"
	"golang.org/x/time/rate"
)

// Message est l'enveloppe échangée avec les appareils (voir PROTOCOL.md).
// Le serveur ne lit que Type et To ; Payload lui est opaque et n'est jamais journalisé.
type Message struct {
	Type    string          `json:"type"`
	To      string          `json:"to,omitempty"`
	From    string          `json:"from,omitempty"`
	Payload json.RawMessage `json:"payload,omitempty"`
}

// Types relayés entre appareils. Tout autre type est refusé.
var relayedTypes = map[string]bool{
	"offer":  true,
	"answer": true,
	"ice":    true,
	"alert":  true,
	"status": true,
}

var idPattern = regexp.MustCompile(`^[A-Za-z0-9_-]{8,64}$`)

func validID(s string) bool { return idPattern.MatchString(s) }

// Limits regroupe les garde-fous appliqués à chaque connexion.
type Limits struct {
	MaxDevicesPerFamily int
	MaxMessageBytes     int64
	MessagesPerSecond   float64
	Burst               int
	WriteTimeout        time.Duration
	PingInterval        time.Duration
}

func DefaultLimits() Limits {
	return Limits{
		MaxDevicesPerFamily: 8,
		MaxMessageBytes:     64 << 10,
		MessagesPerSecond:   20,
		Burst:               40,
		WriteTimeout:        5 * time.Second,
		PingInterval:        20 * time.Second,
	}
}

type client struct {
	family string
	device string
	conn   *websocket.Conn
	send   chan []byte
	cancel context.CancelFunc
}

// Hub met en relation les appareils d'une même famille. Une famille ne voit jamais les autres.
type Hub struct {
	mu       sync.Mutex
	families map[string]map[string]*client
	limits   Limits
	log      *slog.Logger
}

func NewHub(limits Limits, log *slog.Logger) *Hub {
	return &Hub{families: map[string]map[string]*client{}, limits: limits, log: log}
}

var errFamilyFull = errors.New("famille complète")

// join enregistre le client. Si l'appareil était déjà connecté, l'ancienne connexion est fermée.
func (h *Hub) join(c *client) error {
	h.mu.Lock()
	members := h.families[c.family]
	if members == nil {
		members = map[string]*client{}
		h.families[c.family] = members
	}
	old := members[c.device]
	if old == nil && len(members) >= h.limits.MaxDevicesPerFamily {
		h.mu.Unlock()
		return errFamilyFull
	}
	members[c.device] = c
	peers := make([]string, 0, len(members)-1)
	for id := range members {
		if id != c.device {
			peers = append(peers, id)
		}
	}
	h.mu.Unlock()

	if old != nil {
		old.cancel()
	}
	sort.Strings(peers)
	payload, _ := json.Marshal(map[string][]string{"peers": peers})
	h.deliver(c, Message{Type: "peers", Payload: payload})
	h.broadcast(c.family, c.device, Message{Type: "peer_joined", From: c.device})
	return nil
}

func (h *Hub) leave(c *client) {
	h.mu.Lock()
	members := h.families[c.family]
	if members[c.device] != c {
		h.mu.Unlock()
		return // déjà remplacé par une connexion plus récente
	}
	delete(members, c.device)
	if len(members) == 0 {
		delete(h.families, c.family)
	}
	h.mu.Unlock()
	h.broadcast(c.family, c.device, Message{Type: "peer_left", From: c.device})
}

// route relaie un message d'un appareil vers un pair (To renseigné) ou vers toute la famille.
func (h *Hub) route(from *client, msg Message) {
	msg.From = from.device
	if msg.To == "" {
		h.broadcast(from.family, from.device, msg)
		return
	}
	h.mu.Lock()
	target := h.families[from.family][msg.To]
	h.mu.Unlock()
	if target == nil {
		h.deliver(from, Message{Type: "error", Payload: json.RawMessage(`{"code":"peer_offline"}`)})
		return
	}
	h.deliver(target, msg)
}

func (h *Hub) broadcast(family, except string, msg Message) {
	h.mu.Lock()
	targets := make([]*client, 0, len(h.families[family]))
	for id, c := range h.families[family] {
		if id != except {
			targets = append(targets, c)
		}
	}
	h.mu.Unlock()
	for _, t := range targets {
		h.deliver(t, msg)
	}
}

// deliver ne bloque jamais : un appareil trop lent est déconnecté plutôt que de ralentir les autres.
func (h *Hub) deliver(c *client, msg Message) {
	data, err := json.Marshal(msg)
	if err != nil {
		return
	}
	select {
	case c.send <- data:
	default:
		h.log.Warn("file d'envoi pleine, déconnexion", "family", c.family, "device", c.device)
		c.cancel()
	}
}

// serve gère une connexion jusqu'à sa fermeture.
func (h *Hub) serve(ctx context.Context, conn *websocket.Conn, family, device string) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	conn.SetReadLimit(h.limits.MaxMessageBytes)

	c := &client{family: family, device: device, conn: conn, send: make(chan []byte, 64), cancel: cancel}
	if err := h.join(c); err != nil {
		conn.Close(websocket.StatusPolicyViolation, err.Error())
		return
	}
	defer h.leave(c)
	h.log.Info("appareil connecté", "family", family, "device", device)

	go h.writeLoop(ctx, c)

	limiter := rate.NewLimiter(rate.Limit(h.limits.MessagesPerSecond), h.limits.Burst)
	for {
		typ, data, err := conn.Read(ctx)
		if err != nil {
			h.log.Info("appareil déconnecté", "family", family, "device", device, "reason", websocket.CloseStatus(err))
			conn.Close(websocket.StatusNormalClosure, "")
			return
		}
		if !limiter.Allow() {
			conn.Close(websocket.StatusPolicyViolation, "trop de messages")
			return
		}
		if typ != websocket.MessageText {
			h.deliver(c, Message{Type: "error", Payload: json.RawMessage(`{"code":"text_only"}`)})
			continue
		}
		var msg Message
		if err := json.Unmarshal(data, &msg); err != nil || !relayedTypes[msg.Type] {
			h.deliver(c, Message{Type: "error", Payload: json.RawMessage(`{"code":"invalid_message"}`)})
			continue
		}
		if msg.To != "" && !validID(msg.To) {
			h.deliver(c, Message{Type: "error", Payload: json.RawMessage(`{"code":"invalid_recipient"}`)})
			continue
		}
		h.route(c, msg)
	}
}

func (h *Hub) writeLoop(ctx context.Context, c *client) {
	ticker := time.NewTicker(h.limits.PingInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case data := <-c.send:
			wctx, cancel := context.WithTimeout(ctx, h.limits.WriteTimeout)
			err := c.conn.Write(wctx, websocket.MessageText, data)
			cancel()
			if err != nil {
				c.cancel()
				return
			}
		case <-ticker.C:
			pctx, cancel := context.WithTimeout(ctx, h.limits.WriteTimeout)
			err := c.conn.Ping(pctx)
			cancel()
			if err != nil {
				c.cancel()
				return
			}
		}
	}
}
