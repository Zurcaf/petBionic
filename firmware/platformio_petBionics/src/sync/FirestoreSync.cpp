#include "FirestoreSync.h"

#include <SD.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>

// ---------------------------------------------------------------------------
// CSV parsing helpers
// ---------------------------------------------------------------------------
static String csvField(const String &line, const int *cp, int col, int totalCols)
{
    int start = (col == 0) ? 0 : cp[col - 1] + 1;
    int end   = (col < totalCols - 1) ? cp[col] : (int)line.length();
    return line.substring(start, end);
}

// ---------------------------------------------------------------------------
// Anonymous sign-in — returns Firebase ID token or "" on failure.
// ---------------------------------------------------------------------------
String FirestoreSync::getIdToken(WiFiClientSecure &client)
{
    String url = String(kAuthUrl) + "?key=" + kApiKey;

    HTTPClient http;
    http.begin(client, url);
    http.addHeader("Content-Type", "application/json");
    int code = http.POST("{\"returnSecureToken\":true}");
    if (code != 200)
    {
        Serial.printf("[Firestore] Auth failed %d\n", code);
        http.end();
        client.stop();
        return "";
    }

    String resp = http.getString();
    http.end();
    client.stop();
    delay(100);

    int keyIdx = resp.indexOf("\"idToken\":");
    if (keyIdx < 0) return "";
    int start = resp.indexOf('"', keyIdx + 10) + 1;
    int end   = resp.indexOf('"', start);
    if (start <= 0 || end <= start) return "";

    String token = resp.substring(start, end);
    Serial.printf("[Firestore] Auth OK (token %d chars)\n", token.length());
    return token;
}

// ---------------------------------------------------------------------------
// Session metadata document
// ---------------------------------------------------------------------------
int FirestoreSync::uploadSessionDoc(WiFiClientSecure &client,
                                    const String &sessionId,
                                    uint32_t startMs,
                                    const String &idToken)
{
    String url = String(kBaseUrl) + "/sessions/" + sessionId;

    String body =
        "{\"fields\":{"
        "\"sessionId\":{\"stringValue\":\"" + sessionId + "\"},"
        "\"device\":{\"stringValue\":\"PetBionic_01\"},"
        "\"startMs\":{\"integerValue\":\"" + String(startMs) + "\"},"
        "\"timestamp\":{\"integerValue\":\"" + String(startMs) + "\"}"
        "}}";

    HTTPClient http;
    http.setReuse(true);
    http.begin(client, url);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("Authorization", "Bearer " + idToken);
    int code = http.PATCH(body);
    http.end();
    return code;
}

// ---------------------------------------------------------------------------
// Upload one reading via PATCH — reuses TLS connection.
// ---------------------------------------------------------------------------
int FirestoreSync::uploadReading(WiFiClientSecure &client,
                                 const String &sessionId,
                                 int index,
                                 const String &csvLine,
                                 const String &idToken)
{
    const int kCols   = 17;
    const int kCommas = kCols - 1;
    int cp[kCommas];
    int found = 0;

    for (int i = 0; i < (int)csvLine.length() && found < kCommas; ++i)
    {
        if (csvLine[i] == ',') cp[found++] = i;
    }
    if (found < kCommas)
    {
        Serial.printf("[Firestore] Row %d skipped: only %d commas\n", index, found);
        return -1;
    }

    long   t_rel_ms   = csvField(csvLine, cp,  0, kCols).toInt();
    long   t_rel_us   = csvField(csvLine, cp,  1, kCols).toInt();
    String time_local = csvField(csvLine, cp,  2, kCols);
    float  load_raw   = csvField(csvLine, cp,  3, kCols).toFloat();
    float  load_filt  = csvField(csvLine, cp,  4, kCols).toFloat();
    float  ax         = csvField(csvLine, cp,  5, kCols).toFloat();
    float  ay         = csvField(csvLine, cp,  6, kCols).toFloat();
    float  az         = csvField(csvLine, cp,  7, kCols).toFloat();
    float  gx         = csvField(csvLine, cp,  8, kCols).toFloat();
    float  gy         = csvField(csvLine, cp,  9, kCols).toFloat();
    float  gz         = csvField(csvLine, cp, 10, kCols).toFloat();
    float  mx         = csvField(csvLine, cp, 11, kCols).toFloat();
    float  my         = csvField(csvLine, cp, 12, kCols).toFloat();
    float  mz         = csvField(csvLine, cp, 13, kCols).toFloat();
    float  roll       = csvField(csvLine, cp, 14, kCols).toFloat();
    float  pitch      = csvField(csvLine, cp, 15, kCols).toFloat();
    float  yaw        = csvField(csvLine, cp, 16, kCols).toFloat();

    String url = String(kBaseUrl) +
                 "/sessions/" + sessionId +
                 "/readings/" + String(index);

    String body =
        "{\"fields\":{"
        "\"t_rel_ms\":{\"integerValue\":\"" + String(t_rel_ms) + "\"},"
        "\"t_rel_us\":{\"integerValue\":\"" + String(t_rel_us) + "\"},"
        "\"time_local\":{\"stringValue\":\"" + time_local + "\"},"
        "\"load_cell_raw\":{\"doubleValue\":"  + String(load_raw,  3) + "},"
        "\"load_cell_filt\":{\"doubleValue\":" + String(load_filt, 3) + "},"
        "\"imu_ax\":{\"doubleValue\":"  + String(ax,  2) + "},"
        "\"imu_ay\":{\"doubleValue\":"  + String(ay,  2) + "},"
        "\"imu_az\":{\"doubleValue\":"  + String(az,  2) + "},"
        "\"imu_gx\":{\"doubleValue\":"  + String(gx,  2) + "},"
        "\"imu_gy\":{\"doubleValue\":"  + String(gy,  2) + "},"
        "\"imu_gz\":{\"doubleValue\":"  + String(gz,  2) + "},"
        "\"imu_mx\":{\"doubleValue\":"  + String(mx,  2) + "},"
        "\"imu_my\":{\"doubleValue\":"  + String(my,  2) + "},"
        "\"imu_mz\":{\"doubleValue\":"  + String(mz,  2) + "},"
        "\"roll_deg\":{\"doubleValue\":"  + String(roll,  2) + "},"
        "\"pitch_deg\":{\"doubleValue\":" + String(pitch, 2) + "},"
        "\"yaw_deg\":{\"doubleValue\":"   + String(yaw,   2) + "}"
        "}}";

    HTTPClient http;
    http.setReuse(true);
    http.begin(client, url);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("Authorization", "Bearer " + idToken);
    int code = http.PATCH(body);
    if (code < 0)
    {
        // Connection dropped — stop so next call reconnects cleanly
        client.stop();
    }
    http.end();
    return code;
}

// ---------------------------------------------------------------------------
// Main entry point.
// ---------------------------------------------------------------------------
SyncResult FirestoreSync::syncFile(const char *filePath, const String &sessionId)
{
    SyncResult result{false, 0, 0};

    if (!filePath || filePath[0] == '\0')
    {
        Serial.println("[Firestore] syncFile: empty path");
        return result;
    }

    File f = SD.open(filePath, FILE_READ);
    if (!f)
    {
        Serial.printf("[Firestore] Cannot open %s\n", filePath);
        return result;
    }

    Serial.printf("[Firestore] Syncing %s -> sessions/%s\n",
                  filePath, sessionId.c_str());

    WiFiClientSecure client;
    client.setInsecure();

    // ── 1. Anonymous auth ─────────────────────────────────────────────────
    String idToken = getIdToken(client);
    if (idToken.isEmpty())
    {
        Serial.println("[Firestore] Auth failed — aborting sync");
        f.close();
        result.httpErrorCode = -2;
        return result;
    }

    // ── 2. Session metadata doc ───────────────────────────────────────────
    int metaCode = uploadSessionDoc(client, sessionId, millis(), idToken);
    Serial.printf("[Firestore] Session doc: %d\n", metaCode);
    if (metaCode != 200) result.httpErrorCode = metaCode;

    // ── 3. Skip CSV header ────────────────────────────────────────────────
    f.readStringUntil('\n');

    // ── 4. Upload readings — one PATCH per row, TLS connection reused ─────
    int index = 0;
    while (f.available())
    {
        String line = f.readStringUntil('\n');
        line.trim();
        if (line.length() == 0) continue;

        int code = uploadReading(client, sessionId, index, line, idToken);
        if (code == 200)
        {
            result.readingsSynced++;
            if (index % 100 == 0)
                Serial.printf("[Firestore] %d readings uploaded...\n", index + 1);
        }
        else
        {
            result.httpErrorCode = code;
            Serial.printf("[Firestore] Reading %d failed: %d\n", index, code);
        }
        index++;
    }

    f.close();

    Serial.printf("[Firestore] Sync complete — %d/%d readings\n",
                  result.readingsSynced, index);

    result.success = (result.readingsSynced == index && index > 0);
    return result;
}
