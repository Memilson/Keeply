# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS deps
WORKDIR /app

COPY landing/package.json landing/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

FROM node:22-alpine AS build
WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY landing ./

RUN NODE_OPTIONS="--max-old-space-size=1024" npm run build

FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production

COPY --from=build /app/package.json ./package.json
COPY --from=build /app/package-lock.json ./package-lock.json
COPY --from=build /app/.next ./.next
COPY --from=build /app/node_modules ./node_modules

EXPOSE 3000

CMD ["npm", "run", "start", "--", "-H", "0.0.0.0", "-p", "3000"]
