# Middleware

Application middleware Spring Boot publiee par [BLCK-N](https://github.com/BLCK-N).

- Depot : https://github.com/BLCK-N/middleware
- Java : 21
- Build : Maven
- Conteneur : Docker

## Configuration

Copiez `.env.example` vers `.env`, puis renseignez vos variables d'environnement.

Pour Firebase, ne publiez jamais `firebase-service-account.json`.
Configurez une des options suivantes :

- Variable `GOOGLE_APPLICATION_CREDENTIALS` vers un fichier de compte de service local;
- Ajoutez `src/main/resources/firebase-service-account.json` uniquement sur votre environnement local.

## Execution locale

```bash
./mvnw spring-boot:run
```

## Execution Docker

```bash
docker build -t middleware .
docker run --env-file .env -p 8080:8080 middleware
```
