# EyeDroid — kierunek produktu

## Pozycjonowanie

„Zamień telefon w prywatną kamerę do swojego studia”. Początkowy odbiorca: twórca używający OBS, nauczyciel, demonstrator produktów, osoba potrzebująca drugiej kamery. Obietnica: szybkie lokalne połączenie, czytelny obraz i brak konta. Wyróżnik powinien opierać się na stabilności i doświadczeniu użytkownika, nie wyłącznie na ładniejszym klonie.

## Model przychodów — hipotezy do sprawdzenia

| Wariant | Proponowana zawartość | Hipoteza ceny, nie oferta |
|---|---|---|
| Free | 720p, 1 komputer, audio, bez reklam i znaku wodnego, bezpieczne parowanie | 0 zł |
| Pro | 1080p/4K tam, gdzie sprzęt pozwala, preset scen, ręczne focus/exposure, USB, ulepszony transport | 49–79 zł jednorazowo |
| Studio, później | wiele telefonów, wspólny zegar, sceny i klient desktop, obsługa zespołu | abonament dopiero po potwierdzeniu stałej wartości |

To plan komercyjny; bieżący MVP nie wymusza limitu Free ani nie zawiera gotowych funkcji Pro z tej tabeli. Podstawowa prywatność, szyfrowanie oraz zatrzymanie kamery powinny pozostać dostępne we wszystkich planach. Reklamy podczas transmisji pogarszają zaufanie i stabilność. Nie wprowadzaj abonamentu wyłącznie za lokalny strumień bez ciągłej usługi.

Pierwszy test: rozmowy i sesje obserwowane z 10–15 użytkownikami OBS, następnie porównanie deklarowanej wartości z realnym zakupem. Ceny są propozycją do eksperymentu, bez prognozy przychodów. Mierz: czas do pierwszego obrazu, odsetek udanych parowań, sesje bez zerwania, powrót po tygodniu i konwersję; telemetria wyłącznie jawna i dobrowolna, nigdy obrazy/audio.

## Kolejne etapy

1. **Walidacja MVP:** prawdziwe telefony, CI, długie sesje, zmiana sieci, blokada ekranu, zużycie CPU/baterii, obsługa odmów pozwoleń i wolnych odbiorców.
2. **Transport produkcyjny:** WebRTC + sprzętowy H.264/Opus, timestamps i synchronizacja, adaptacja bitrate; MJPEG zachować dla kompatybilności. Kamera jako producent klatek; kodek i transport jako wymienne implementacje.
3. **Połączenie i wygoda:** parowanie QR z potwierdzeniem odbiorcy, NSD/mDNS, odwoływanie dostępu, orientacja, zoom, focus, wskaźnik temperatury, dostępność i lokalizacje.
4. **Desktop/OBS:** dedykowane doświadczenie połączenia, podpisane instalatory, dopiero później kamera wirtualna i routowanie audio; najpierw jeden system operacyjny.
5. **Płatności:** Play Billing po ponownej weryfikacji bieżących wymogów, przywracanie zakupów, testy anulowania/zwrotu/pending, walidacja entitlement i przejrzysty zakup jednorazowy. Brak sztucznych przycisków „Kup”, zanim ścieżka faktycznie działa.

## Warunki przed komercyjną publikacją

Udany build release i audyt zależności; podpisywanie poza repozytorium; testy uprawnień i urządzeń; polityka prywatności odpowiadająca rzeczywistemu przepływowi danych; poprawne deklaracje usług foreground w sklepie. Weryfikację zasad sklepu, licencji bibliotek, podatków i prawa konsumenckiego trzeba przeprowadzić dla wybranego rynku przed sprzedażą. Ta notatka jest planem produktu, nie analizą prawną.

Nie używaj nazwy, ikon, zrzutów ekranu ani kodu DroidCam jako własnych. Repozytorium jest publiczne: sekretów, kluczy podpisujących i kluczy zakupowych nie wolno commitować.
