"""Machine-readable Android update contract shared by release packaging/verifying."""
import hashlib
import json
import re

APPLICATION_ID = "com.aegisvault.mobile"
MAX_APK_BYTES = 128 * 1024 * 1024
SIGNER = "b2b0f013dcfa428bb6f4c3563faa204c7890f72abd6062d1cbbb155222d493c9"


def manifest(version, code, apk, signer, min_sdk=26):
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", version):
        raise ValueError("Unsupported product version")
    if not isinstance(code, int) or isinstance(code, bool) or not 0 < code <= 2147483647:
        raise ValueError("Invalid Android versionCode")
    if not 0 < apk.stat().st_size <= MAX_APK_BYTES:
        raise ValueError("APK exceeds updater budget")
    data = {"schemaVersion": 1, "applicationId": APPLICATION_ID,
            "versionName": version, "versionCode": code, "minSdk": min_sdk,
            "releaseTag": f"v{version}", "apk": {"name": apk.name,
                "size": apk.stat().st_size, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                "signingCertificateSha256": signer}}
    validate_manifest(data, f"v{version}", apk.name, data["apk"]["sha256"])
    return data


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON field")
        result[key] = value
    return result


def validate_manifest(data, tag, apk_name, digest):
    def integer(value, maximum):
        return type(value) is int and 0 < value <= maximum
    if (type(data) is not dict or type(data.get("schemaVersion")) is not int or data["schemaVersion"] != 1
            or data.get("applicationId") != APPLICATION_ID or data.get("releaseTag") != tag
            or data.get("versionName") != tag.removeprefix("v")
            or not re.fullmatch(r"v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", tag)
            or any(int(part) > 2147483647 for part in tag[1:].split('.'))
            or not integer(data.get("versionCode"), 2147483647)
            or not integer(data.get("minSdk"), 2147483647) or data["minSdk"] < 26):
        raise ValueError("Invalid update manifest metadata")
    apk = data.get("apk")
    if (type(apk) is not dict or apk.get("name") != apk_name
            or apk_name != f"AegisVaultMobile-{tag}-debug.apk"
            or not integer(apk.get("size"), MAX_APK_BYTES)
            or apk.get("sha256") != digest or not re.fullmatch(r"[0-9a-f]{64}", digest)
            or apk.get("signingCertificateSha256") != SIGNER):
        raise ValueError("Invalid update APK declaration")


def write_manifest(path, data):
    path.write_text(json.dumps(data, ensure_ascii=True, sort_keys=True, indent=2) + "\n", encoding="utf-8")
