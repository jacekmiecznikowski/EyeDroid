# Walidacja EyeDroid 0.1

## Sprawdzone w środowisku przygotowania

- 8 testów JUnit 4 zakończonych sukcesem: YUV z paddingiem i pixel stride, pozycja bufora/crop, obrót VU, odrzucanie nieprawidłowego portu/rozdzielczości/FPS/lampy.
- Testy uruchomiono rzeczywiście na JVM 17, kompilując pliki `Yuv420.kt`, `StreamState.kt` oraz oba pliki testowe kompilatorem Kotlin 2.2.21 z dystrybucji Gradle. To test czystej logiki; nie jest równoważny kompilacji Android z Kotlin 2.4.21.
- `node --check`: składnia JavaScript odbiornika poprawna.
- Wszystkie pliki XML parsują się poprawnie.
- Gradle wrapper z oficjalnej dystrybucji 9.3.1, z przypiętym SHA-256.

## Pełny build Android

Próba `testDebugUnitTest lintDebug assembleDebug` była zablokowana na pobieraniu pluginu AGP: klient Java/Gradle otrzymywał `java.net.SocketException: Network is unreachable`. Metadane i POM AGP 9.1.1 były dostępne przez klienta HTTP środowiska. SDK 37.0 i Build Tools 36.0.0 udało się zainstalować. Nie oznacza to udanej kompilacji aplikacji.

Workflow `.github/workflows/android.yml` uruchamia unit tests, lint i build APK w GitHub Actions. Przed użyciem komercyjnym wymagany jest zielony wynik tego workflow. Nie zadeklarowano przejścia testów kamery, audio, termiki, blokady ekranu ani integracji OBS/VLC bez urządzenia i odbiorników.

## Testy akceptacyjne na telefonach

| Scenariusz | Oczekiwany rezultat |
|---|---|
| Odmowa CAMERA | Brak kamery i usługi, czytelny błąd |
| Mikrofon wyłączony przed startem | Obraz działa bez RECORD_AUDIO; `/audio` odrzucane |
| Odmowa lokalnej sieci na API 37 | Brak startu; komunikat o pozwoleniach |
| Odmowa powiadomień na API 33+ | Start zgodny z systemem; zatrzymanie w aplikacji działa |
| Wi-Fi bez dostępu do internetu | Lokalna transmisja nadal działa |
| Brak Wi-Fi, VPN, sieć IPv6-only | Jasny komunikat; nie pokazujemy fikcyjnego IP |
| Zmiana sieci/IP | Sesja zakończona; ponowny start daje nowy URL/token |
| Port zajęty | Błąd, zwolniona kamera/mikrofon/wake lock |
| Obrót/recreation Activity | Usługa i strumień pozostają aktywne |
| Ekran zablokowany przez 30 min | Pomiar ciągłości i zużycia baterii na każdym OEM |
| Stop w powiadomieniu | Kamera, mikrofon, sockety i wake lock zwolnione |
| 20 cykli start/stop | Brak konfliktu portu, wycieków i starej klatki |
| Przełączenie przód/tył | Rebind bez zawieszenia; odrzucenie lampy przedniej |
| 480p → 720p → 1080p | Rzeczywisty rozmiar pokazany, fallback poprawny |
| Brak lampy / brak przedniej kamery | Czytelny błąd i odtworzenie działającej konfiguracji |
| Zły/brak tokenu, cross-origin POST | Brak obrazu, audio i zmiany konfiguracji |
| 5. połączenie mediów | Odrzucenie; aktualne połączenia nadal działają |
| Powolny/nieczytający klient | Pamięć stabilna, kamera nadal działa, klient rozłączony |
| Zerwanie WebSocket / mikrofon zajęty | Bez crasha; widoczny status i możliwość wznowienia |
| Chrome/Firefox/Edge/OBS/VLC | Rzeczywiste odtwarzanie; zanotowana wersja odbiornika |

## Pomiary przed wydaniem

Minimum trzy urządzenia różnych producentów i Android 12/14/16/17, oba obiektywy, słabe Wi-Fi i 5 GHz, 60-minutowe sesje. Zapisuj FPS rzeczywisty, bitrate, pamięć, temperaturę, energię i opóźnienie p50/p95. Dla audio sprawdź zanik próbek, jitter, zachowanie po uśpieniu karty i dryf względem obrazu. Nie publikuj deklaracji „low latency” lub „4K/60” bez tych pomiarów.
