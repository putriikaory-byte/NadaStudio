# NadaStudio

Aplikasi Android (Kotlin + Jetpack Compose) untuk membuat lagu: Channel Rack,
Piano Roll, keyboard, mixer, rekam vokal, dan auto aransemen berdasarkan genre.

## Aransemen dari Vokal (multi-bar, lokal)

Fitur ini mengubah rekaman suara menjadi lagu penuh tanpa internet, API,
atau layanan AI eksternal:

1. Buka tab **Auto Lagu / Vokal → 🎤 Vokal**, rekam suara (pakai headset
   supaya musik tidak bocor ke mikrofon) atau upload file audio.
2. Pilih genre, lalu tekan **🎼 Aransemen dari Vokal**.
3. Aplikasi menganalisis nada vokal (YIN), mendeteksi kunci, memperkirakan
   tempo dari onset nada, lalu membuat aransemen sepanjang rekaman:
   drum, bass, dan akor mengikuti akor tiap bar yang dipilih dari nada
   vokal di bar itu; vokal tetap menjadi melodi utama, dan counter-melody
   hanya muncul di bar tanpa vokal. Bar dikelompokkan menjadi
   Intro / Verse / Chorus dari kepadatan vokal.
4. Tekan **Play** untuk mendengar lagu penuh bersama vokal. Piano Roll /
   Channel Rack menampilkan bar 1; edit di sana mengubah bar 1.
5. Ekspor dengan **🎧 Ekspor WAV lagu penuh** (jumlah bar = panjang lagu),
   atau simpan proyek seperti biasa — proyek lama (format v=3, 1 bar)
   tetap bisa dibuka.

Catatan: ini aransemen berbasis aturan (rule-based) dari nada yang
terdeteksi, bukan model generatif seperti Suno — hasilnya mengikuti
vokal dan genre, dan tetap bisa diedit manual di Piano Roll.
