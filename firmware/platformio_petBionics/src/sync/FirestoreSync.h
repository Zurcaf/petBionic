#pragma once

#include <Arduino.h>
#include <WiFiClientSecure.h>

struct SyncResult
{
    bool success;
    int  readingsSynced;
    int  httpErrorCode;
};

class FirestoreSync
{
public:
    SyncResult syncFile(const char *filePath, const String &sessionId);

private:
    static constexpr const char *kApiKey  = FIRESTORE_API_KEY;
    static constexpr const char *kBaseUrl =
        "https://firestore.googleapis.com/v1/projects/" FIREBASE_PROJECT
        "/databases/(default)/documents";
    static constexpr const char *kAuthUrl =
        "https://identitytoolkit.googleapis.com/v1/accounts:signUp";

    String getIdToken(WiFiClientSecure &client);
    int    uploadSessionDoc(WiFiClientSecure &client,
                            const String &sessionId,
                            uint32_t startMs,
                            const String &idToken);
    int    uploadReading(WiFiClientSecure &client,
                         const String &sessionId,
                         int index,
                         const String &csvLine,
                         const String &idToken);
};
