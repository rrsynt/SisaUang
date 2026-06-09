# BRD — DompetKu

**Business Requirements Document** · Dokumen untuk manusia (latar bisnis), bukan instruksi teknis untuk agent.

## 1. Latar Belakang

Banyak orang gagal mencatat keuangan pribadi secara konsisten bukan karena tidak
mau, tapi karena proses pencatatannya merepotkan. Data tersebar di banyak tempat:
rekening bank, e-wallet, aplikasi investasi (saham, reksa dana), deposito, emas,
dan kas tunai. Tidak ada satu tampilan yang langsung menjawab "berapa kekayaan
bersih saya sekarang, dan apakah bulan ini surplus atau defisit?"

## 2. Masalah yang Diselesaikan

- Input transaksi terlalu ribet sehingga pengguna malas mencatat.
- Tidak ada gambaran utuh kondisi keuangan (cash flow + aset + investasi).
- Data keuangan tersebar dan sulit direkap.

## 3. Tujuan Bisnis

- Pengguna bisa mencatat satu transaksi dalam waktu kurang dari 5 detik.
- Pengguna melihat net worth, surplus/defisit, dan komposisi aset dalam satu layar.
- Pencatatan sebagian terotomasi (notifikasi, import, OCR) sehingga beban manual berkurang.

## 4. Kriteria Sukses

- Pengguna mencatat transaksi secara rutin selama minimal 30 hari berturut-turut
  tanpa merasa terbebani.
- Rekap bulanan akurat dan cocok dengan saldo riil rekening/dompet.
- Aplikasi lolos kebijakan Google Play tanpa pelanggaran izin (lihat SRS bagian Compliance).

## 5. Stakeholder

- Pengguna utama & pemilik produk: pengembang sendiri (penggunaan pribadi).
- Tidak ada pihak ketiga yang menerima data keuangan.

## 6. Batasan & Asumsi

- Aplikasi single-user, untuk satu pemilik perangkat.
- Mata uang utama IDR.
- Data milik pengguna sepenuhnya; sinkronisasi hanya ke akun Google milik pengguna.

## 7. Risiko Utama

- **Kepatuhan Play Store**: risiko penolakan jika menggunakan izin terlarang
  (SMS, penyalahgunaan Accessibility). Dimitigasi dengan metode input yang legal saja.
- **Privasi**: data finansial sensitif. Dimitigasi dengan pemrosesan on-device dan
  tanpa server pihak ketiga.
- **Akurasi parsing**: notifikasi/CSV bisa salah baca. Dimitigasi dengan sistem
  draft yang harus dikonfirmasi pengguna sebelum tersimpan.
