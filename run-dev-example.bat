@echo off
REM primer run-dev datoteke
set AZURE_CONNECTION_STRING=DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1
set AZURE_CONTAINER_NAME=slopeoasisfiles
set SERVER_PORT=8081
REM spremen glede na tvoj Clerk account
set CLERK_ISSUER=YOUR_CLERK_ISSUER_URL
set CLERK_JWKS_URL=YOUR_CLERK_JWKS_URL
set JWT_DEV_MODE=false
set MAX_FILE_SIZE=500MB
set MAX_REQUEST_SIZE=600MB
set FILE_SIZE_THRESHOLD=5MB
set TOMCAT_MAX_SWALLOW_SIZE=-1

mvn spring-boot:run
