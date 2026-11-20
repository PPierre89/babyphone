# Étape de compilation
FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/signaling .

# Image finale : distroless, sans shell ni gestionnaire de paquets, utilisateur non root.
FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/signaling /signaling
USER nonroot:nonroot
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD ["/signaling", "-healthcheck"]
ENTRYPOINT ["/signaling"]
