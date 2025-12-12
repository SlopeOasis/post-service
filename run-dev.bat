@echo off
REM Sets Azurite env vars and runs post-service on port 8081, logs to error.log
set AZURE_CONNECTION_STRING=DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1
set AZURE_CONTAINER_NAME=slopeoasisfiles
set SERVER_PORT=8081
REM Stream logs to this terminal
mvn spring-boot:run