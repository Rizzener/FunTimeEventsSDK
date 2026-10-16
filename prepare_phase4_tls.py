"""Local fixture certificate/truststore only; never changes JVM/system trust."""
import datetime, ipaddress, json, subprocess, re, shutil
from pathlib import Path
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from run_phase2 import ROOT

if __name__=='__main__':
    prefix=['rtk','proxy'] if shutil.which('rtk') else []
    dest=ROOT/'.evidence/phase4-tls';dest.mkdir(parents=True,exist_ok=True)
    cert=dest/'server-cert.pem';key=dest/'server-key.pem';store=dest/'trust-full.p12'
    now=datetime.datetime.now(datetime.timezone.utc)
    renew=not cert.exists() or not key.exists()
    if not renew:
        renew=x509.load_pem_x509_certificate(cert.read_bytes()).not_valid_after_utc<=now+datetime.timedelta(days=1)
    if renew:
        private=rsa.generate_private_key(public_exponent=65537,key_size=2048)
        name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'FunTime Phase4 local fixture')])
        c=(x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(private.public_key())
           .serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(days=1))
           .not_valid_after(now+datetime.timedelta(days=30)).add_extension(x509.SubjectAlternativeName([
               x509.DNSName('localhost'),x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]),critical=False)
           .sign(private,hashes.SHA256()))
        cert.write_bytes(c.public_bytes(serialization.Encoding.PEM))
        key.write_bytes(private.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
        store.unlink(missing_ok=True)
    if not store.exists():
        settings=subprocess.run(prefix+['java','-XshowSettings:properties','-version'],capture_output=True,text=True,errors='replace')
        java_home=Path(re.search(r'java.home = ([^\r\n]+)',settings.stdout+settings.stderr).group(1).strip())
        import_roots=subprocess.run(prefix+['keytool','-importkeystore','-noprompt','-srckeystore',str(java_home/'lib/security/cacerts'),
            '-srcstorepass','changeit','-destkeystore',str(store),'-deststoretype','PKCS12','-deststorepass','phase4-test'],capture_output=True)
        if import_roots.returncode:raise RuntimeError(import_roots.stdout+import_roots.stderr)
        p=subprocess.run(prefix+['keytool','-importcert','-noprompt','-storetype','PKCS12','-alias','phase4-fixture',
            '-file',str(cert),'-keystore',str(store),'-storepass','phase4-test'],capture_output=True)
        if p.returncode:raise RuntimeError(p.stdout+p.stderr)
    print('Local TLS ready:',store)
