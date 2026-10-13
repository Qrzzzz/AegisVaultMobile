# Offline migration of historical Android text

Starting with 1.2, the App decrypts formal desktop AGV1 only. Android JSON AGV1 from 1.1 or earlier, old AES and AK# require explicit migration.

Keep a separate original ciphertext backup and work on a copy. AK# embeds its password: anyone who has that token also has its password. Migration cannot undo an earlier disclosure. Never place AK# in command arguments, chat, logs, network services or a synchronized clipboard.

Prepare Python 3.11+, `cryptography>=42.0` and `scripts/migrate-legacy.py` on a trusted offline computer. Dependency acquisition is environment preparation; migration itself has no network operation. No desktop App or server is required. This is explicit recovery of one text token, not App file encryption or batch processing.

Save only one old token in `old-token.txt`. In an interactive terminal with hidden password input:

```powershell
python scripts/migrate-legacy.py --format android-json --input old-token.txt --output new-token.txt
```

- `android-json`: historical Android JSON `AGV1.`
- `aes`: old standard Base64 nonce+ciphertext/tag
- `ak`: `AK#password#ciphertext`; the old password is taken from that token

AES/JSON prompt for the old password. All formats prompt for a new password and confirmation. Passwords are hidden and never command arguments; the tool refuses getpass's echoing fallback. Use an interactive terminal and do not redirect password input. Choose a different new password for AK data. The output must not exist; neither input nor an existing output can be overwritten.

Authentication, strict UTF-8 and resource checks complete before the tool generates a formal AGV1 token with fresh salt/nonce and writes output. Console output contains only success or a fixed error category. Verify the complete recovered text in both the new Android App and desktop using the new password before considering removal of old copies. Keep the original backup until recovery is confirmed.

Wrong passwords, corruption, a mismatched explicit format or exceeded budgets fail without a successful output token. Limits remain 1 MiB plaintext/password, 2 MiB input, 64 MiB scrypt memory/4,194,304 work; historical JSON metadata remains limited to 16 KiB and 32 container levels. No parameter lowering, automatic fallback or password guessing is performed.

Python/crypto libraries cannot guarantee immediate wiping of all memory copies. Exit the tool and protect both text files. Test fixtures are artificial public data; never put real secrets into repository tests or interoperability evidence.
