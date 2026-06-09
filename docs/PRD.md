# PRD — DompetKu

**Product Requirements Document** · Apa yang dibangun dari sisi produk. Agent wajib merujuk ke dokumen ini dan TIDAK menambah fitur di luar daftar ini tanpa persetujuan pengguna.

## 1. Ringkasan Produk

Aplikasi Android native untuk manajemen keuangan pribadi single-user di Indonesia.
Melacak cash flow, aset, dan investasi dalam satu tempat dengan input super cepat
dan otomasi yang legal sesuai kebijakan Play.

## 2. Target Pengguna

Satu pengguna (pemilik perangkat) yang ingin mencatat keuangan tanpa ribet dan
melihat kondisi finansial menyeluruh. Terbiasa dengan bank, e-wallet, dan
aplikasi investasi di Indonesia.

## 3. Prinsip Produk

- **Cepat**: catat transaksi < 5 detik.
- **Jujur & aman**: data milik pengguna, diproses on-device.
- **Otomasi yang dikonfirmasi**: hasil otomasi selalu jadi draft, bukan langsung tersimpan.
- **Offline-first**: berfungsi penuh tanpa internet; sync hanya pelengkap.

## 4. Fitur (per Fase)

### Fase 1 — Inti Pencatatan (MVP)
- Catat pemasukan & pengeluaran manual (nominal, kategori, akun, tanggal, catatan).
- Kategori default contoh: kos, makan, bensin, dll. (dapat disesuaikan pengguna).
- Daftar transaksi dengan filter tanggal & kategori.
- Manajemen akun/dompet (kas, rekening bank, e-wallet).
- Dashboard ringkas: saldo total, pemasukan vs pengeluaran bulan ini, surplus/defisit.

### Fase 2 — Aset & Net Worth
- Pencatatan aset: tabungan, deposito, saham, reksa dana, obligasi, emas, kripto.
- Perhitungan net worth (total aset − total kewajiban).
- Komposisi aset (grafik proporsi).
- Update nilai aset secara manual (harga/saldo terbaru).

### Fase 3 — Otomasi Input
- **Parsing notifikasi** (`NotificationListenerService`): WAJIB ada layar
  prominent disclosure & consent SEBELUM aktif. Toggle global + per aplikasi
  sumber. Rule engine (pola/regex untuk nominal, debit/kredit, merchant, akun)
  dengan template bawaan bank/e-wallet Indonesia + editor pola buatan pengguna
  dengan fitur "Uji Pola". Hasil → antrian draft + deduplikasi.
- **Import CSV/Excel**: pemetaan kolom + preview sebelum simpan.
- **OCR struk/bukti transaksi**: ekstrak nominal & tanggal → draft.
- **Parser teks/suara bebas**: contoh "kopi 25rb gopay" → draft transaksi.
- Semua metode di atas menghasilkan **draft yang dikonfirmasi pengguna**.

### Fase 4 — Laporan & Sinkronisasi
- Laporan bulanan: arus kas, per kategori, tren.
- Ekspor data.
- **Sinkronisasi Google Sheets DUA ARAH** via ID spreadsheet yang dimasukkan
  pengguna (akun Google pengguna sendiri), strategi last-write-wins + soft-delete
  agar konsisten lintas perangkat. Backup ke Google Drive yang dapat dipulihkan utuh.
- Multi-device melalui sync (sumber kebenaran tetap data lokal per perangkat).
- **Kunci aplikasi** PIN/biometrik dan data lokal terenkripsi (wajib).

## 5. Alur Pengguna Utama

1. Buka app → langsung lihat dashboard.
2. Tekan tombol "+" → input transaksi cepat → simpan (< 5 detik).
3. Notifikasi bank masuk → app membuat draft → pengguna konfirmasi 1 tap.
4. Akhir bulan → lihat laporan → (opsional) sync ke Google Sheets.

## 6. Di Luar Lingkup (Out of Scope)

- Multi-user / akun bersama.
- Membaca data internal aplikasi lain secara langsung (tidak mungkin & melanggar kebijakan).
- Izin SMS, `QUERY_ALL_PACKAGES`, atau penyalahgunaan Accessibility (lihat SRS).
- Pengiriman data ke server pihak ketiga.

## 7. Prioritas

Fase 1 → 2 → 3 → 4 secara berurutan. Jangan mulai fase berikutnya sebelum fase
sebelumnya berfungsi dan disetujui pengguna.
