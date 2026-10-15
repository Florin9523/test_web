# Abac pentru Android

Aplicația Android împachetează calculatorul din `../calculator` într-un WebView.
Merge complet offline și nu cere nicio permisiune.

- Pachet: `io.github.florin9523.abac`, versiunea 1.0
- Android 7.0 (API 24) sau mai nou, țintă API 35
- Istoricul și setările rămân salvate pe telefon
- Butonul Înapoi închide întâi istoricul
- Temă luminoasă sau întunecată, după setarea telefonului

## Compilare

Pe Ubuntu, cu un JDK instalat:

```sh
sudo apt install aapt dalvik-exchange zipalign apksigner android-sdk-platform-23
./build.sh
```

Rezultatul este `build/Abac.apk`. La prima rulare se creează cheia de semnare
`abac-release.jks`; păstreaz-o, pentru că o actualizare se instalează peste
aplicația existentă doar dacă e semnată cu aceeași cheie.
