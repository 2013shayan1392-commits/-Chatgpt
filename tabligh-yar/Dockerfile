FROM node:22-alpine

ENV NODE_ENV=production
ENV PORT=3000
ENV RECEIPTS_DIR=/var/data/receipts

WORKDIR /app
COPY package*.json ./
RUN npm install --omit=dev && npm cache clean --force
COPY --chown=node:node . .
RUN mkdir -p /var/data/receipts && chown -R node:node /var/data
USER node

EXPOSE 3000
CMD ["npm", "start"]
