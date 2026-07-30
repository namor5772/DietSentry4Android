// prefs.cpp — SharedPreferences equivalent: a JSON file in
// %APPDATA%\DietSentry4Windows\prefs.json, saved on every put.
#include "app.h"
#include "json.hpp"

using nlohmann::json;

static std::wstring prefsPath() {
    return appDataDirectory() + L"/prefs.json";
}

static json& J(void*& impl) {
    if (!impl) impl = new json(json::object());
    return *(json*)impl;
}

void Prefs::load() {
    auto text = readTextFile(prefsPath());
    json& j = J(impl);
    if (text) {
        json parsed = json::parse(*text, nullptr, false);
        if (!parsed.is_discarded() && parsed.is_object()) j = parsed;
    }
}

void Prefs::save() {
    writeTextFile(prefsPath(), J(impl).dump(2));
}

int Prefs::getInt(const char* key, int def) const {
    const json& j = J(const_cast<void*&>(impl));
    auto it = j.find(key);
    return (it != j.end() && it->is_number()) ? it->get<int>() : def;
}

bool Prefs::getBool(const char* key, bool def) const {
    const json& j = J(const_cast<void*&>(impl));
    auto it = j.find(key);
    return (it != j.end() && it->is_boolean()) ? it->get<bool>() : def;
}

long long Prefs::getLong(const char* key, long long def) const {
    const json& j = J(const_cast<void*&>(impl));
    auto it = j.find(key);
    return (it != j.end() && it->is_number()) ? it->get<long long>() : def;
}

std::string Prefs::getString(const char* key, const std::string& def) const {
    const json& j = J(const_cast<void*&>(impl));
    auto it = j.find(key);
    return (it != j.end() && it->is_string()) ? it->get<std::string>() : def;
}

void Prefs::putInt(const char* key, int v)    { J(impl)[key] = v; save(); }
void Prefs::putBool(const char* key, bool v)  { J(impl)[key] = v; save(); }
void Prefs::putLong(const char* key, long long v) { J(impl)[key] = v; save(); }
void Prefs::putString(const char* key, const std::string& v) { J(impl)[key] = v; save(); }
void Prefs::remove(const char* key) { J(impl).erase(key); save(); }
