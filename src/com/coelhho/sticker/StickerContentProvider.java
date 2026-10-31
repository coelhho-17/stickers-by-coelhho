package com.coelhho.sticker;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Serve os pacotes de figurinhas para o WhatsApp via ContentProvider.
 *
 * Estrutura em disco (espelha o formato padrão do WhatsApp):
 *   filesDir/packs/contents.json            -> manifesto de todos os pacotes
 *   filesDir/packs/<identifier>/tray.png    -> ícone da bandeja (PNG)
 *   filesDir/packs/<identifier>/*.webp      -> figurinhas (WebP com alpha)
 */
public class StickerContentProvider extends ContentProvider {

    private static final String TAG = "StickerContentProvider";

    public static final String AUTHORITY_PRIMARY = "com.coelhho.sticker.stickercontentprovider";
    public static final String AUTHORITY_ALT = "com.coelhho.sticker.StickerContentProvider";

    private static final int M_METADATA = 1;
    private static final int M_METADATA_ID = 2;
    private static final int M_STICKERS = 3;
    private static final int M_ASSET = 4;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    private static final String[] METADATA_COLUMNS = {
            "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
            "sticker_pack_icon", "android_play_store_link", "ios_app_download_link",
            "sticker_pack_publisher_email", "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
            "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack"
    };

    private static final String[] STICKER_COLUMNS = {"sticker_file_name", "sticker_emoji"};

    private File packsDir;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) return false;
        packsDir = new File(context.getFilesDir(), "packs");
        if (!packsDir.exists() && !packsDir.mkdirs()) return false;

        String[] authorities = {AUTHORITY_PRIMARY, AUTHORITY_ALT};
        for (String authority : authorities) {
            MATCHER.addURI(authority, "metadata", M_METADATA);
            MATCHER.addURI(authority, "metadata/*", M_METADATA_ID);
            MATCHER.addURI(authority, "stickers/*", M_STICKERS);
            MATCHER.addURI(authority, "stickers_asset/*/*", M_ASSET);
        }
        return true;
    }

    private JSONObject readContents() {
        File file = new File(packsDir, "contents.json");
        if (!file.exists()) return new JSONObject();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int off = 0, r;
            while (off < buf.length && (r = fis.read(buf, off, buf.length - off)) > 0) off += r;
            return new JSONObject(new String(buf, "UTF-8"));
        } catch (Exception e) {
            Log.w(TAG, "readContents failed", e);
            return new JSONObject();
        }
    }

    private JSONObject findPack(String identifier) {
        JSONArray packs = readContents().optJSONArray("sticker_packs");
        if (packs != null) {
            for (int i = 0; i < packs.length(); i++) {
                JSONObject p = packs.optJSONObject(i);
                if (p != null && identifier.equals(p.optString("identifier"))) {
                    return p;
                }
            }
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        int match = MATCHER.match(uri);
        if (match == M_METADATA || match == M_METADATA_ID) {
            return queryMetadata(uri, match);
        }
        if (match == M_STICKERS) {
            return queryStickers(uri);
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private Cursor queryMetadata(Uri uri, int match) {
        String wantedId = (match == M_METADATA_ID) ? uri.getLastPathSegment() : null;
        JSONObject root = readContents();
        String playLink = root.optString("android_play_store_link", "");
        String iosLink = root.optString("ios_app_store_link", "");

        MatrixCursor cursor = new MatrixCursor(METADATA_COLUMNS);
        JSONArray packs = root.optJSONArray("sticker_packs");
        if (packs != null) {
            for (int i = 0; i < packs.length(); i++) {
                JSONObject p = packs.optJSONObject(i);
                if (p == null) continue;
                String id = p.optString("identifier", "");
                if (wantedId != null && !wantedId.equals(id)) continue;
                cursor.addRow(new Object[]{
                        id,
                        p.optString("name", ""),
                        p.optString("publisher", ""),
                        p.optString("tray_image_file", ""),
                        playLink,
                        iosLink,
                        p.optString("publisher_email", ""),
                        p.optString("publisher_website", ""),
                        p.optString("privacy_policy_website", ""),
                        p.optString("license_agreement_website", ""),
                        p.optString("image_data_version", "1"),
                        p.optBoolean("avoid_cache", false) ? 1 : 0,
                        p.optBoolean("animated_sticker_pack", false) ? 1 : 0
                });
            }
        }
        return cursor;
    }

    private Cursor queryStickers(Uri uri) {
        String id = uri.getLastPathSegment();
        MatrixCursor cursor = new MatrixCursor(STICKER_COLUMNS);
        JSONObject pack = findPack(id);
        if (pack != null) {
            JSONArray stickers = pack.optJSONArray("stickers");
            if (stickers != null) {
                for (int i = 0; i < stickers.length(); i++) {
                    JSONObject s = stickers.optJSONObject(i);
                    if (s == null) continue;
                    JSONArray emojis = s.optJSONArray("emojis");
                    StringBuilder sb = new StringBuilder();
                    if (emojis != null) {
                        for (int j = 0; j < emojis.length(); j++) {
                            if (j > 0) sb.append(',');
                            sb.append(emojis.optString(j, ""));
                        }
                    }
                    cursor.addRow(new Object[]{s.optString("image_file", ""), sb.toString()});
                }
            }
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        int match = MATCHER.match(uri);
        if (match == M_ASSET) {
            String name = uri.getLastPathSegment();
            return name.endsWith(".webp") ? "image/webp" : "image/png";
        }
        if (match == M_STICKERS) return "vnd.android.cursor.dir/stickers";
        if (match == M_METADATA || match == M_METADATA_ID) return "vnd.android.cursor.dir/metadata";
        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) {
        int match = MATCHER.match(uri);
        if (match != M_ASSET) return null;
        List<String> segments = uri.getPathSegments();
        if (segments.size() < 3) return null;
        String identifier = segments.get(segments.size() - 2);
        String fileName = segments.get(segments.size() - 1);
        File file = new File(new File(packsDir, identifier), fileName);
        try {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) {
            Log.w(TAG, "openFile failed: " + file, e);
            return null;
        }
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("Not supported");
    }
}
