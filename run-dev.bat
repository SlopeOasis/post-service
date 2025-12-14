@echo off
REM Runs post-service on port 8081 with Clerk JWT configuration - DEV MODE (no signature validation)
set AZURE_CONNECTION_STRING=DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1
set AZURE_CONTAINER_NAME=slopeoasisfiles
set SERVER_PORT=8081
set CLERK_ISSUER=https://upright-bird-25.clerk.accounts.dev
set CLERK_JWKS_URL=https://upright-bird-25.clerk.accounts.dev/.well-known/jwks.json
set JWT_DEV_MODE=false
mvn spring-boot:run