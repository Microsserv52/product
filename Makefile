.PHONY: setup bootstrap start stop reset test verify lint format-check build smoke clean docs

setup:
	cp -n .env.example .env || true

bootstrap:
	./scripts/bootstrap.sh

start:
	docker compose up --detach --build

stop:
	./scripts/stop.sh

reset:
	./scripts/reset.sh

test:
	./mvnw --batch-mode test

verify:
	./mvnw --batch-mode verify

lint:
	./mvnw --batch-mode validate

format-check:
	git diff --check
	! grep -R -n -E '^(<<<<<<<|=======|>>>>>>>)' --exclude-dir=.git --exclude-dir=target .

build:
	docker build --tag $${PRODUCT_IMAGE:-microsserv52-product:local} .

smoke:
	./scripts/smoke.sh

clean:
	./mvnw --batch-mode clean
	rm -rf site

docs:
	zensical build --clean
