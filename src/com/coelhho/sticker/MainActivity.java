package com.coelhho.sticker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * App minimalista: escolhe imagem -> (opcional) apaga fundo -> exporta como
 * figurinha do WhatsApp SEM o contorno branco do criador nativo.
 */
public class MainActivity extends Activity {

    private static final String TAG = "StickerLimpo";
    private static final int REQ_PICK = 1;

    private EraseView eraseView;
    private EditText nameInput;
    private Button eraseButton;
    private boolean eraseOn = false;
    private List<GifDecoder.Frame> gifFrames; // null = imagem estática

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())
                && intent.getType() != null
                && intent.getType().startsWith("image/")) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) loadImage(uri);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.setBackgroundColor(Color.parseColor("#F4F4F4"));

        TextView title = new TextView(this);
        title.setText("Stickers by coelhho");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#111111"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(10));
        root.addView(title);

        nameInput = new EditText(this);
        nameInput.setHint("Nome da figurinha (opcional)");
        nameInput.setSingleLine(true);
        root.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        eraseView = new EraseView(this);
        LinearLayout.LayoutParams cvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(eraseView, cvLp);

        final TextView brushLabel = new TextView(this);
        brushLabel.setText("Tamanho da borracha: 48");
        brushLabel.setTextColor(Color.parseColor("#333333"));
        root.addView(brushLabel);

        SeekBar brushBar = new SeekBar(this);
        brushBar.setMax(100);
        brushBar.setProgress(24);
        brushBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float size = Math.max(8f, progress * 2f);
                eraseView.setBrushSize(size);
                brushLabel.setText("Tamanho da borracha: " + (int) size);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        root.addView(brushBar);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        Button pickButton = new Button(this);
        pickButton.setText("Imagem");
        pickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });
        row1.addView(pickButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        eraseButton = new Button(this);
        eraseButton.setText("Borracha");
        eraseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                eraseOn = !eraseOn;
                eraseView.setEraseMode(eraseOn);
                eraseButton.setText(eraseOn ? "Borracha: ON" : "Borracha");
            }
        });
        row1.addView(eraseButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button clearButton = new Button(this);
        clearButton.setText("Recomeçar");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                eraseView.clearImage();
            }
        });
        row1.addView(clearButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(row1);

        Button addButton = new Button(this);
        addButton.setText("Adicionar ao WhatsApp");
        addButton.setTextColor(Color.WHITE);
        addButton.setBackgroundColor(Color.parseColor("#25D366"));
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addToWhatsApp();
            }
        });
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = dp(12);
        root.addView(addButton, addLp);

        setContentView(root);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, REQ_PICK);
        } catch (ActivityNotFoundException e) {
            toast("Nenhum app para escolher imagem");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            loadImage(data.getData());
        }
    }

    private void loadImage(Uri uri) {
        byte[] bytes = readBytes(uri);
        if (bytes == null || bytes.length == 0) {
            toast("Erro ao abrir a imagem");
            return;
        }

        if (isGif(bytes)) {
            try {
                List<GifDecoder.Frame> frames = GifDecoder.decode(bytes);
                if (frames.size() > 1) {
                    gifFrames = frames;
                    Bitmap first = Bitmap.createBitmap(
                            frames.get(0).argb, frames.get(0).width, frames.get(0).height,
                            Bitmap.Config.ARGB_8888);
                    eraseView.setImage(first);
                    eraseView.setEraseMode(false);
                    eraseOn = false;
                    eraseButton.setEnabled(false);
                    eraseButton.setText("Borracha (GIF)");
                    return;
                }
            } catch (Exception e) {
                android.util.Log.w(TAG, "GIF decode falhou; tratando como estático", e);
            }
        }

        gifFrames = null;
        eraseButton.setEnabled(true);
        eraseButton.setText("Borracha");
        decodeStatic(bytes);
    }

    private byte[] readBytes(Uri uri) {
        try (InputStream in = openImageStream(uri)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) baos.write(buf, 0, r);
            return baos.toByteArray();
        } catch (Exception e) {
            android.util.Log.e(TAG, "readBytes failed", e);
            return null;
        }
    }

    private boolean isGif(byte[] b) {
        return b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
    }

    private void decodeStatic(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        if (bmp != null) {
            eraseView.setImage(bmp);
        } else {
            toast("Não foi possível ler a imagem");
        }
    }

    private InputStream openImageStream(Uri uri) throws Exception {
        if ("file".equals(uri.getScheme())) {
            return new FileInputStream(new File(uri.getPath()));
        }
        return getContentResolver().openInputStream(uri);
    }

    private void addToWhatsApp() {
        boolean hasImage = (gifFrames != null) || eraseView.hasImage();
        if (!hasImage) {
            toast("Escolha uma imagem primeiro");
            return;
        }

        String identifier = "pack_" + System.currentTimeMillis();
        String custom = nameInput.getText().toString().trim();
        String packName = custom.isEmpty()
                ? "Sticker " + (System.currentTimeMillis() / 1000)
                : custom;

        File packsDir = new File(getFilesDir(), "packs");
        if (!packsDir.exists() && !packsDir.mkdirs()) {
            toast("Erro ao criar pasta");
            return;
        }
        File packDir = new File(packsDir, identifier);
        if (!packDir.mkdirs()) {
            toast("Erro ao criar pacote");
            return;
        }

        String webpName = "sticker0.webp";
        String trayName = "tray.png";

        try {
            byte[] webp;
            Bitmap traySource;
            boolean animated;
            if (gifFrames != null) {
                webp = encodeAnimatedWebpUnderLimit(gifFrames);
                if (webp.length == 0) {
                    toast("Não foi possível animar este GIF");
                    return;
                }
                Bitmap first = Bitmap.createBitmap(
                        gifFrames.get(0).argb, gifFrames.get(0).width, gifFrames.get(0).height,
                        Bitmap.Config.ARGB_8888);
                traySource = EraseView.fitBitmap(first, 512);
                animated = true;
            } else {
                Bitmap sticker = eraseView.getBitmap();
                webp = encodeWebpUnderLimit(sticker);
                traySource = sticker;
                animated = false;
            }

            // 1) WebP (estático ou animado) com alpha — evita o contorno branco
            try (FileOutputStream fos = new FileOutputStream(new File(packDir, webpName))) {
                fos.write(webp);
            }

            // 2) Ícone da bandeja (PNG 96x96 sobre fundo branco)
            Bitmap tray = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            Canvas trayCanvas = new Canvas(tray);
            trayCanvas.drawColor(Color.WHITE);
            trayCanvas.drawBitmap(traySource, null, new RectF(0, 0, 96, 96), null);
            try (FileOutputStream fos = new FileOutputStream(new File(packDir, trayName))) {
                tray.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            tray.recycle();

            // 3) contents.json (acumula os pacotes já criados)
            writeContentsJson(identifier, packName, webpName, trayName, animated);

            // 4) Intent padrão de importação do WhatsApp
            Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
            intent.putExtra("sticker_pack_id", identifier);
            intent.putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY_ALT);
            intent.putExtra("sticker_pack_name", packName + " ");
            startActivityForResult(intent, 0);
        } catch (ActivityNotFoundException e) {
            toast("WhatsApp não está instalado");
        } catch (Exception e) {
            toast("Erro ao exportar: " + e.getMessage());
        }
    }

    // Codifica o bitmap como WebP (com alpha) garantindo que fique sob o limite
    // do WhatsApp p/ sticker estático (~100 KB). WEBP_LOSSY preserva alpha e é
    // bem menor que lossless; reduz a qualidade até caber.
    private byte[] encodeWebpUnderLimit(Bitmap bmp) {
        int limit = 90 * 1024; // ~90 KB, folga sob o teto real de ~101 KB
        for (int q = 90; q >= 30; q -= 10) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, q, baos);
            byte[] out = baos.toByteArray();
            if (out.length <= limit) return out;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 20, baos);
        return baos.toByteArray();
    }

    // Codifica GIF animado como WebP animado respeitando os limites do WhatsApp
    // p/ figurinha ANIMADA: <= ~500 KB e <= 10s. Reduz qualidade e corta frames.
    private byte[] encodeAnimatedWebpUnderLimit(List<GifDecoder.Frame> frames) {
        int limit = 480 * 1024; // folga sob o teto real de ~500 KB
        for (int q = 90; q >= 30; q -= 10) {
            byte[] out = encodeAnimated(frames, q);
            if (out != null && out.length <= limit) return out;
        }
        byte[] out = encodeAnimated(frames, 20);
        return (out != null) ? out : new byte[0];
    }

    private byte[] encodeAnimated(List<GifDecoder.Frame> frames, int quality) {
        List<AnimatedWebpMuxer.Frame> muxFrames = new ArrayList<>();
        int total = 0;
        for (GifDecoder.Frame gf : frames) {
            int dur = Math.max(20, gf.delayMs); // mínimo 20ms
            if (total + dur > 10000) break;     // estoura 10s -> corta o resto
            total += dur;

            Bitmap bmp = Bitmap.createBitmap(gf.argb, gf.width, gf.height, Bitmap.Config.ARGB_8888);
            Bitmap fitted = EraseView.fitBitmap(bmp, 512);
            if (fitted == null) return null;
            byte[] webp = compressLossy(fitted, quality);
            StaticWebpParser.Result r = StaticWebpParser.parse(webp);
            muxFrames.add(new AnimatedWebpMuxer.Frame(r.payload, r.alph, r.lossless, 512, 512, dur));
            fitted.recycle();
            bmp.recycle();
        }
        if (muxFrames.isEmpty()) return null;
        return AnimatedWebpMuxer.mux(muxFrames, 512, 512, 0);
    }

    private byte[] compressLossy(Bitmap bmp, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, baos);
        return baos.toByteArray();
    }

    private void writeContentsJson(String identifier, String packName,
                                   String webpName, String trayName, boolean animated) throws Exception {
        File contentsFile = new File(getFilesDir(), "packs/contents.json");

        JSONObject root = new JSONObject();
        JSONArray packs = new JSONArray();

        if (contentsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(contentsFile)) {
                byte[] buf = new byte[(int) contentsFile.length()];
                int off = 0, r;
                while (off < buf.length && (r = fis.read(buf, off, buf.length - off)) > 0) off += r;
                JSONObject existing = new JSONObject(new String(buf, "UTF-8"));
                JSONArray existingPacks = existing.optJSONArray("sticker_packs");
                if (existingPacks != null) {
                    for (int i = 0; i < existingPacks.length(); i++) {
                        JSONObject p = existingPacks.optJSONObject(i);
                        if (p != null) packs.put(p);
                    }
                }
            } catch (Exception ignored) {
                packs = new JSONArray();
            }
        }

        JSONObject pack = new JSONObject();
        pack.put("identifier", identifier);
        pack.put("name", packName);
        pack.put("publisher", "coelhho");
        pack.put("tray_image_file", trayName);
        pack.put("publisher_email", "");
        pack.put("publisher_website", "");
        pack.put("privacy_policy_website", "");
        pack.put("license_agreement_website", "");
        pack.put("image_data_version", "1");
        pack.put("avoid_cache", false);
        pack.put("animated_sticker_pack", animated);

        JSONArray stickers = new JSONArray();
        JSONObject s = new JSONObject();
        s.put("image_file", webpName);
        JSONArray emojis = new JSONArray();
        emojis.put("\uD83D\uDE00");
        s.put("emojis", emojis);
        stickers.put(s);
        pack.put("stickers", stickers);

        packs.put(pack);
        root.put("android_play_store_link", "");
        root.put("ios_app_store_link", "");
        root.put("sticker_packs", packs);

        try (FileOutputStream fos = new FileOutputStream(contentsFile)) {
            fos.write(root.toString().getBytes("UTF-8"));
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
