# SpringMarket frontend

React and TypeScript SPA for SpringMarket authentication, shopping, seller
listings, and order administration.

## Local development

```bash
cp .env.example .env
npm install
npm run dev
```

The backend defaults to `http://localhost:8080`. Override `VITE_API_BASE_URL` in
`.env` when using another API origin.

From the repository root, start the backend dependencies with:

```bash
docker compose up -d
cd backend
./mvnw spring-boot:run
```

Then run the frontend in a separate terminal. Buyer and seller accounts can be
created from the registration page. Admin accounts must already exist because
public registration intentionally rejects the `ADMIN` role. MailHog is available
at `http://localhost:8025`.

## Checks

```bash
npm test
npm run lint
npm run build
```
