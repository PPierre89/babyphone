package main

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"
)

const family = "famille_test1"

func newTestServer(t *testing.T, limits Limits) *httptest.Server {
	t.Helper()
	cfg := Config{AllowedFamilies: map[string]bool{family: true, "famille_autre": true}, Limits: limits}
	srv := httptest.NewServer(NewHandler(cfg, NewHub(limits, slog.New(slog.NewTextHandler(io.Discard, nil)))))
	t.Cleanup(srv.Close)
	return srv
}

func dial(t *testing.T, srv *httptest.Server, fam, device string) *websocket.Conn {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	url := "ws" + strings.TrimPrefix(srv.URL, "http") + "/ws?family=" + fam + "&device=" + device
	conn, _, err := websocket.Dial(ctx, url, nil)
	if err != nil {
		t.Fatalf("connexion %s : %v", device, err)
	}
	t.Cleanup(func() { conn.CloseNow() })
	return conn
}

func read(t *testing.T, conn *websocket.Conn) Message {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	_, data, err := conn.Read(ctx)
	if err != nil {
		t.Fatalf("lecture : %v", err)
	}
	var m Message
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatalf("JSON : %v", err)
	}
	return m
}

func write(t *testing.T, conn *websocket.Conn, s string) {
	t.Helper()
	if err := conn.Write(context.Background(), websocket.MessageText, []byte(s)); err != nil {
		t.Fatalf("écriture : %v", err)
	}
}

func TestHealthz(t *testing.T) {
	srv := newTestServer(t, DefaultLimits())
	resp, err := http.Get(srv.URL + "/healthz")
	if err != nil || resp.StatusCode != http.StatusOK {
		t.Fatalf("healthz : %v %v", err, resp)
	}
}

func TestRejectsInvalidRequests(t *testing.T) {
	srv := newTestServer(t, DefaultLimits())
	cases := map[string]int{
		"/ws?family=court&device=telephone_1":            http.StatusBadRequest,
		"/ws?family=" + family + "&device=../../etc":     http.StatusBadRequest,
		"/ws?family=famille_inconnue&device=telephone_1": http.StatusForbidden,
	}
	for path, want := range cases {
		resp, err := http.Get(srv.URL + path)
		if err != nil {
			t.Fatal(err)
		}
		if resp.StatusCode != want {
			t.Errorf("%s : code %d, attendu %d", path, resp.StatusCode, want)
		}
	}
}

func TestRejectsBrowserOrigin(t *testing.T) {
	srv := newTestServer(t, DefaultLimits())
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	url := "ws" + strings.TrimPrefix(srv.URL, "http") + "/ws?family=" + family + "&device=telephone_1"
	_, resp, err := websocket.Dial(ctx, url, &websocket.DialOptions{HTTPHeader: http.Header{"Origin": {"https://pirate.example"}}})
	if err == nil || resp == nil || resp.StatusCode != http.StatusForbidden {
		t.Fatalf("une page web ne doit pas pouvoir se connecter : %v", err)
	}
}

func TestPeersAndRouting(t *testing.T) {
	srv := newTestServer(t, DefaultLimits())

	baby := dial(t, srv, family, "bebe_chambre")
	if m := read(t, baby); m.Type != "peers" || string(m.Payload) != `{"peers":[]}` {
		t.Fatalf("premier message inattendu : %+v", m)
	}

	parent := dial(t, srv, family, "parent_pierre")
	if m := read(t, parent); m.Type != "peers" || string(m.Payload) != `{"peers":["bebe_chambre"]}` {
		t.Fatalf("liste des pairs inattendue : %+v", m)
	}
	if m := read(t, baby); m.Type != "peer_joined" || m.From != "parent_pierre" {
		t.Fatalf("annonce attendue : %+v", m)
	}

	// Le serveur impose l'émetteur : impossible de se faire passer pour un autre appareil.
	write(t, parent, `{"type":"offer","to":"bebe_chambre","from":"usurpateur","payload":{"sdp":"v=0"}}`)
	m := read(t, baby)
	if m.Type != "offer" || m.From != "parent_pierre" || string(m.Payload) != `{"sdp":"v=0"}` {
		t.Fatalf("offre mal relayée : %+v", m)
	}

	// Diffusion à toute la famille quand aucun destinataire n'est donné.
	write(t, baby, `{"type":"alert","payload":{"kind":"cry"}}`)
	if m := read(t, parent); m.Type != "alert" || m.From != "bebe_chambre" {
		t.Fatalf("alerte non diffusée : %+v", m)
	}
}

func TestFamiliesAreIsolated(t *testing.T) {
	srv := newTestServer(t, DefaultLimits())
	a := dial(t, srv, family, "bebe_chambre")
	read(t, a)
	b := dial(t, srv, "famille_autre", "intrus_0001")
	read(t, b)

	write(t, b, `{"type":"offer","to":"bebe_chambre"}`)
	if m := read(t, b); m.Type != "error" || !strings.Contains(string(m.Payload), "peer_offline") {
		t.Fatalf("une autre famille ne doit pas joindre cet appareil : %+v", m)
	}
}

func TestRejectsUnknownTypes(t *testing.T) {
	srv := newTestServer(t, DefaultLimits())
	c := dial(t, srv, family, "bebe_chambre")
	read(t, c)
	write(t, c, `{"type":"peers"}`)
	if m := read(t, c); m.Type != "error" || !strings.Contains(string(m.Payload), "invalid_message") {
		t.Fatalf("un client ne doit pas pouvoir forger un message serveur : %+v", m)
	}
}

func TestOversizedMessageClosesConnection(t *testing.T) {
	limits := DefaultLimits()
	limits.MaxMessageBytes = 1024
	srv := newTestServer(t, limits)
	c := dial(t, srv, family, "bebe_chambre")
	read(t, c)
	write(t, c, `{"type":"ice","payload":"`+strings.Repeat("x", 2048)+`"}`)
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	if _, _, err := c.Read(ctx); websocket.CloseStatus(err) != websocket.StatusMessageTooBig {
		t.Fatalf("fermeture attendue pour message trop gros, obtenu %v", err)
	}
}

func TestRateLimit(t *testing.T) {
	limits := DefaultLimits()
	limits.MessagesPerSecond = 1
	limits.Burst = 3
	srv := newTestServer(t, limits)
	c := dial(t, srv, family, "bebe_chambre")
	read(t, c)
	for i := 0; i < 10; i++ {
		_ = c.Write(context.Background(), websocket.MessageText, []byte(`{"type":"status"}`))
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	if _, _, err := c.Read(ctx); websocket.CloseStatus(err) != websocket.StatusPolicyViolation {
		t.Fatalf("fermeture attendue pour excès de messages, obtenu %v", err)
	}
}

func TestFamilyCapacity(t *testing.T) {
	limits := DefaultLimits()
	limits.MaxDevicesPerFamily = 1
	srv := newTestServer(t, limits)
	first := dial(t, srv, family, "bebe_chambre")
	read(t, first)
	second := dial(t, srv, family, "parent_pierre")
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	if _, _, err := second.Read(ctx); websocket.CloseStatus(err) != websocket.StatusPolicyViolation {
		t.Fatalf("la famille devrait être complète, obtenu %v", err)
	}
}

func TestReconnectReplacesOldConnection(t *testing.T) {
	srv := newTestServer(t, DefaultLimits())
	parent := dial(t, srv, family, "parent_pierre")
	read(t, parent)
	old := dial(t, srv, family, "bebe_chambre")
	read(t, old)
	read(t, parent) // peer_joined

	fresh := dial(t, srv, family, "bebe_chambre")
	if m := read(t, fresh); m.Type != "peers" {
		t.Fatalf("liste des pairs attendue : %+v", m)
	}
	write(t, parent, `{"type":"offer","to":"bebe_chambre"}`)
	// Le parent reçoit d'abord l'annonce de la nouvelle connexion, puis l'offre arrive sur celle-ci.
	if m := read(t, parent); m.Type != "peer_joined" {
		t.Fatalf("annonce attendue : %+v", m)
	}
	if m := read(t, fresh); m.Type != "offer" {
		t.Fatalf("l'offre doit arriver sur la nouvelle connexion : %+v", m)
	}
}

func TestConfigRequiresAllowedFamilies(t *testing.T) {
	t.Setenv("ALLOWED_FAMILIES", "")
	if _, err := configFromEnv(); err == nil {
		t.Fatal("le serveur ne doit pas démarrer sans liste de familles")
	}
	t.Setenv("ALLOWED_FAMILIES", "famille_ok1, mauvais/id")
	if _, err := configFromEnv(); err == nil {
		t.Fatal("un identifiant invalide doit être refusé")
	}
	t.Setenv("ALLOWED_FAMILIES", "famille_ok1,famille_ok2")
	cfg, err := configFromEnv()
	if err != nil || len(cfg.AllowedFamilies) != 2 {
		t.Fatalf("configuration valide refusée : %v", err)
	}
}
