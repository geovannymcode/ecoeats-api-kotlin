# Build the pinned official releases; public minio/minio images are unavailable.
FROM golang:1.25-alpine AS server-build
RUN apk add --no-cache git
RUN git clone --depth 1 --branch RELEASE.2025-10-15T17-29-55Z https://github.com/minio/minio.git /src
WORKDIR /src
RUN CGO_ENABLED=0 go build -trimpath -o /out/minio .

FROM golang:1.25-alpine AS client-build
RUN apk add --no-cache git
RUN git clone --depth 1 --branch RELEASE.2025-08-13T08-35-41Z https://github.com/minio/mc.git /src
WORKDIR /src
RUN CGO_ENABLED=0 go build -trimpath -o /out/mc .

FROM alpine:3.22 AS client
RUN apk add --no-cache ca-certificates
COPY --from=client-build /out/mc /usr/local/bin/mc
ENTRYPOINT ["mc"]

FROM alpine:3.22 AS server
RUN apk add --no-cache ca-certificates curl
COPY --from=server-build /out/minio /usr/local/bin/minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
