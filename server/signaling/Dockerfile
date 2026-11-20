# syntax=docker/dockerfile:1
# Étape de compilation, exécutée sur l'architecture du runner et compilée en croisé
# pour chaque cible (amd64, arm64) : bien plus rapide que l'émulation QEMU.
FROM --platform=$BUILDPLATFORM golang:1.27-alpine AS build
ARG TARGETOS
ARG TARGETARCH
ARG VERSION=dev
WORKDIR /src
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download
COPY *.go ./
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -trimpath -ldflags="-s -w -X main.version=${VERSION}" -o /out/signaling .

# Image finale : distroless, sans shell ni gestionnaire de paquets, utilisateur non root.
FROM gcr.io/distroless/static-debian12:nonroot
ARG VERSION=dev
LABEL org.opencontainers.image.title="babyphone-signaling" \
      org.opencontainers.image.description="Serveur de signalisation du babyphone" \
      org.opencontainers.image.source="https://github.com/PPierre89/babyphone" \
      org.opencontainers.image.version="${VERSION}"
COPY --from=build /out/signaling /signaling
USER nonroot:nonroot
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD ["/signaling", "-healthcheck"]
ENTRYPOINT ["/signaling"]
