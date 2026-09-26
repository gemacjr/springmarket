# SpringMarket frontend

React and TypeScript SPA for SpringMarket authentication and catalog browsing.

## Local development

```bash
cp .env.example .env
npm install
npm run dev
```

The backend defaults to `http://localhost:8080`. Override `VITE_API_BASE_URL` in
`.env` when using another API origin.

## Checks

```bash
npm run lint
npm run build
```
