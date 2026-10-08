FROM node:20-bookworm-slim

ENV NODE_ENV=production
WORKDIR /opt/app

COPY package*.json ./
RUN npm install --omit=dev --ignore-scripts

COPY src ./src
COPY scripts ./scripts

RUN mkdir -p /opt/data /opt/backups && chown -R node:node /opt/app /opt/data /opt/backups

USER node
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD node -e "require('http').get('http://127.0.0.1:3000/api/health',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))"

CMD ["node","src/index.js"]
