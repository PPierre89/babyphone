// Serveur de signalisation du babyphone.
//
// Il met en relation les téléphones d'une même famille et relaie leurs messages
// WebRTC (offres, réponses, candidats ICE) et leurs alertes. Il ne voit jamais
// l'audio ni la vidéo, et ne stocke rien sur disque.
//
// Phase 0 : l'accès est limité à une liste de familles autorisées (ALLOWED_FAMILIES).
// Phase 2 : chaque appareil s'authentifiera en signant un défi avec sa clé Keystore.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/coder/websocket"
)

// version est injectée au build : go build -ldflags "-X main.version=1.2.3".
var version = "dev"

type Config struct {
	ListenAddr      string
	AllowedFamilies map[string]bool
	Limits          Limits
}

func configFromEnv() (Config, error) {
	cfg := Config{ListenAddr: ":8080", AllowedFamilies: map[string]bool{}, Limits: DefaultLimits()}
	if v := os.Getenv("LISTEN_ADDR"); v != "" {
		cfg.ListenAddr = v
	}
	for _, f := range strings.Split(os.Getenv("ALLOWED_FAMILIES"), ",") {
		f = strings.TrimSpace(f)
		if f == "" {
			continue
		}
		if !validID(f) {
			return cfg, fmt.Errorf("identifiant de famille invalide dans ALLOWED_FAMILIES : %q", f)
		}
		cfg.AllowedFamilies[f] = true
	}
	if len(cfg.AllowedFamilies) == 0 {
		return cfg, errors.New("ALLOWED_FAMILIES est obligatoire tant que l'authentification des appareils (phase 2) n'existe pas")
	}
	return cfg, nil
}

// NewHandler expose /healthz et /ws.
func NewHandler(cfg Config, hub *Hub) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "text/plain")
		_, _ = w.Write([]byte("ok"))
	})
	mux.HandleFunc("GET /ws", func(w http.ResponseWriter, r *http.Request) {
		family := r.URL.Query().Get("family")
		device := r.URL.Query().Get("device")
		if !validID(family) || !validID(device) {
			http.Error(w, "identifiants invalides", http.StatusBadRequest)
			return
		}
		if !cfg.AllowedFamilies[family] {
			http.Error(w, "famille non autorisée", http.StatusForbidden)
			return
		}
		// Les clients sont des applications Android, pas des navigateurs : pas d'en-tête Origin
		// attendu. Une requête qui en porte un vient d'une page web et est refusée.
		if r.Header.Get("Origin") != "" {
			http.Error(w, "origine refusée", http.StatusForbidden)
			return
		}
		conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{InsecureSkipVerify: true})
		if err != nil {
			return
		}
		hub.serve(r.Context(), conn, family, device)
	})
	return securityHeaders(mux)
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Cache-Control", "no-store")
		next.ServeHTTP(w, r)
	})
}

func healthcheck(addr string) int {
	host := addr
	if strings.HasPrefix(host, ":") {
		host = "127.0.0.1" + host
	}
	client := http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get("http://" + host + "/healthz")
	if err != nil || resp.StatusCode != http.StatusOK {
		return 1
	}
	return 0
}

func main() {
	check := flag.Bool("healthcheck", false, "vérifie que le serveur local répond (pour Docker)")
	showVersion := flag.Bool("version", false, "affiche la version et quitte")
	flag.Parse()
	if *showVersion {
		fmt.Println(version)
		return
	}

	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	if *check {
		addr := os.Getenv("LISTEN_ADDR")
		if addr == "" {
			addr = ":8080"
		}
		os.Exit(healthcheck(addr))
	}

	cfg, err := configFromEnv()
	if err != nil {
		log.Error("configuration invalide", "error", err)
		os.Exit(1)
	}

	srv := &http.Server{
		Addr:              cfg.ListenAddr,
		Handler:           NewHandler(cfg, NewHub(cfg.Limits, log)),
		ReadHeaderTimeout: 5 * time.Second,
		MaxHeaderBytes:    8 << 10,
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = srv.Shutdown(shutdown)
	}()

	log.Info("serveur de signalisation démarré", "version", version, "addr", cfg.ListenAddr, "families", len(cfg.AllowedFamilies))
	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Error("arrêt sur erreur", "error", err)
		os.Exit(1)
	}
}
