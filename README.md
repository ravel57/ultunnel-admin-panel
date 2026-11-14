Запуск в docker compose:
-
```
sudo docker compose build --no-cache && sudo docker-compose up -d 
```

control.ultunnel.ru
-
Отдельный эндпоинт того же приложения: принимает от клиентского приложения
посещённые домены и даёт пользователю управлять раздельной маршрутизацией.
Авторизация — по secret key, который генерирует админка.

* описание и API — [SPLIT_ROUTING.md](SPLIT_ROUTING.md)
* конфигурация nginx — [nginx/control.ultunnel.ru.conf](nginx/control.ultunnel.ru.conf)
