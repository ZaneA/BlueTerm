echo Compiling...
mkdir -p output &&
javac -target 1.4 -source 1.4 -bootclasspath /opt/wtk/lib/cldcapi11.jar:/opt/wtk/lib/midpapi20.jar BlueTerm.java TelnetCanvas.java CustomFont.java &&
preverify -classpath /opt/wtk/lib/cldcapi11.jar:/opt/wtk/lib/midpapi20.jar BlueTerm TelnetCanvas CustomFont &&
cd output && jar cvfm BlueTerm.jar Manifest.mf BlueTerm.class TelnetCanvas.class CustomFont.class font.png icon.png &&
echo OK.
