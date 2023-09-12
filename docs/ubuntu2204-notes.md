
# Note on using Vision SDK on Ubuntu 22.04 LTS

This page includes additional setup instructions beyond what is described in the relevant setup page for Linux for your programming Language. Find those instructions here: [Python](../samples/python/image-analysis/README.md), [C#](../samples/csharp/image-analysis/dotnet/README-Linux.md), [Java](../samples/java/image-analysis/README.md), [C++](../samples/cpp/image-analysis/README-Linux.md).

The Vision SDK depends on OpenSSL library (`libssl`) version 1.x. It has not yet been updated to depend on OpenSSL 3.0. Ubuntu version 22.04 no longer includes `libssl` version 1.x since support for it is ending soon. Therefore applications using Vision SDK will not be able to run on Ubuntu 22.04 unless `libssl` version 1.x is installed. In an upcoming release of Vision SDK this dependency will be fixed. 

To run on Ubuntu 22.04, you must install OpenSSL 1.x from sources. Do the following:
```Bash
wget -O - https://www.openssl.org/source/openssl-1.1.1u.tar.gz | tar zxf -
cd openssl-1.1.1u
./config --prefix=/usr/local
make -j $(nproc)
sudo make install_sw install_ssldirs
sudo ldconfig -v
export SSL_CERT_DIR=/etc/ssl/certs
```
Notes on installation:
- Check https://www.openssl.org/source/ for the latest OpenSSL 1.x version to use. There may be a newer version than the `1.1.1u` mentioned above.
- The setting of `SSL_CERT_DIR` must be in effect system-wide or at least in the console where applications that use the Vision SDK are launched from, otherwise OpenSSL 1.x installed in `/usr/local` may not find certificates.
- Ensure that the console output from `ldconfig -v` includes `/usr/local/lib` as it should on modern systems by default. If this isn't the case, set `LD_LIBRARY_PATH` (with the same scope as `SSL_CERT_DIR`) to add `/usr/local/lib` to the library path:
  ```Bash
  export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH
  ```

Then continue installing dependent packages as described for other Ubuntu versions:
```Bash
sudo apt-get update
sudo apt-get install build-essential libssl-dev wget
```

