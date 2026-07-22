FROM node:22-alpine

WORKDIR /frontend
COPY package.json pnpm-lock.yaml ./
RUN npm install

COPY . ./
EXPOSE 5173
CMD ["npm", "run", "dev"]
