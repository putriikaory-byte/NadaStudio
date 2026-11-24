# NadaStudio

Aplikasi Android (Kotlin + Jetpack Compose) untuk membuat lagu: Channel Rack,
Piano Roll, keyboard, mixer, rekam vokal, dan auto aransemen berdasarkan genre.

## Aransemen dari Vokal (multi-bar, lokal)

Fitur ini mengubah rekaman suara menjadi lagu penuh tanpa internet, API,
atau layanan AI eksternal:

1. Buka tab **Auto Lagu / Vokal → 🎤 Vokal**, rekam suara (pakai headset
   supaya musik tidak bocor ke mikrofon) atau upload file audio.
   Saat rekam, metronom count-in berbunyi 1 bar (4 ketuk, angka 4..1
   tampil di layar) dulu, baru rekaman dan lagu mulai — jadi vokal jatuh
   tepat di ketukan 1. Count-in bisa dimatikan lewat switch-nya.
2. Pilih genre dan tempo proyek, lalu tekan **🎼 Aransemen dari Vokal**.
3. Aplikasi menganalisis nada vokal (YIN), mendeteksi kunci, memotong
   leading silence supaya onset nada pertama jatuh di ketukan 1, lalu
   men-stretch vokal ke tempo proyek (WSOLA, pitch tetap) supaya vokal
   nempel grid dari bar pertama sampai terakhir. Kalau rasio stretch
   terlalu ekstrem (<0,7x atau >1,4x), BPM proyek justru mengikuti tempo
   vokal dan hal itu ditulis di ringkasan.
4. Aransemen dibuat sepanjang rekaman: drum, bass, dan akor mengikuti
   akor tiap bar yang dipilih dari nada vokal di bar itu; vokal tetap
   menjadi melodi utama, dan counter-melody hanya muncul di bar tanpa
   vokal. Bar dikelompokkan menjadi Intro / Verse / Chorus dari
   kepadatan vokal.
5. Tekan **Play** untuk mendengar lagu penuh bersama vokal. **Playlist**
   di bawah panel vokal menampilkan klip vokal (waveform) di atas grid
   bar — klip bisa digeser (drag atau tombol, snap 1 step) dan posisinya
   dipakai saat Play maupun ekspor WAV. **Piano Roll / Channel Rack**
   sekarang bisa mengedit tiap bar: pilih bar di pemilih bar, dan garis
   merah menunjukkan bar yang sedang dimainkan.
6. Ekspor dengan **🎧 Ekspor WAV lagu penuh** (jumlah bar = panjang lagu),
   atau simpan proyek seperti biasa — proyek lama (format v=3, 1 bar)
   tetap bisa dibuka; posisi klip di Playlist ikut tersimpan.

Catatan: ini aransemen berbasis aturan (rule-based) dari nada yang
terdeteksi, bukan model generatif seperti Suno — hasilnya mengikuti
vokal dan genre, dan tetap bisa diedit manual di Piano Roll.
