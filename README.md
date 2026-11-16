# Сервис обработки заявок

REST API технической поддержки 

## 1. Технологии и запуск

Установка:

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose openjdk-21-jdk curl python3 openssl
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
sudo install -d /usr/local/lib/docker/cli-plugins
sudo ln -sf /usr/bin/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
git clone https://github.com/Zuika1166/practika_ychhhh.git
cd practika_ychhhh
bash scripts/init-env.sh
docker compose up -d --build --wait --wait-timeout 180
curl --fail http://127.0.0.1:8080/actuator/health/readiness
python3 scripts/smoke.py
```


## 2. Итог

На виртуальной машине подтверждены 56 тестов на JDK 21.0.13  

Первая: run - https://github.com/Zuika1166/practika_ychhhh/actions/runs/43745510914 ,№1 - https://github.com/Zuika1166/practika_ychhhh/pull/1
Вторая: run - https://github.com/Zuika1166/practika_ychhhh/actions/runs/43756431655 , №2 - https://github.com/Zuika1166/practika_ychhhh/pull/2

Digest manifest -первый - `sha256:27ce77f539f7766a7237b22a7a9e5042e15e8fe381da956a13b6dc0af16cece6`; второй - `sha256:108dc47bf75ec137a998f13fbb16b97258096ae48d15a5614fd67d4282b744dc`
