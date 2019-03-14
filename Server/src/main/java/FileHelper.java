import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class FileHelper {

	public static File getFileByUUIDString(String uuidString) {
		try {
			final File[] addons = listAddonFiles();

			for (File addon : addons) {
				if (!addon.getName().endsWith(".jar")) continue;

				JarFile jar = new JarFile(addon);
				JsonObject addonJson = addonJsonForJarFile(jar);
				String thisUuidString = addonJson.getAsJsonPrimitive("uuid").getAsString();

				if (thisUuidString.equals(uuidString)) {
					return addon;
				}
			}
			return null;
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public static File addonFolder() {
		return new File("addon-cache/");
	}

	public static File[] listAddonFiles() throws IOException {
		File addonsFolder = addonFolder();
		if (!addonsFolder.exists()) {
			addonsFolder.mkdirs();
		}

		File[] files = addonsFolder.listFiles();
		if (files == null) throw new IOException();

		return Arrays.stream(files).filter(f -> f.getName().endsWith(".jar")).sorted(Comparator.comparing(File::getName)).toArray(File[]::new);
	}

	public static File getCacheDir() {
		File cacheFolder = new File("cache/");
		if (!cacheFolder.exists()) {
			cacheFolder.mkdirs();
		}

		return cacheFolder;
	}

	public static String getLastStableVersion() {
		File latestStableVersion = new File("lastStableVersion.txt");
		if (!latestStableVersion.exists()) {
			return null;
		}

		try {
			return new BufferedReader(new InputStreamReader(new FileInputStream(latestStableVersion))).lines().collect(Collectors.joining("\n"));
		} catch (FileNotFoundException e) {
			throw new AssertionError(e);
		}

	}

	public static JsonObject addonJsonForJarFile(JarFile addonFile) throws IOException {
		JarEntry addonJson = addonFile.getJarEntry("addon.json");
		InputStream is = addonFile.getInputStream(addonJson);
		String content = new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
		return (JsonObject) new JsonParser().parse(content);
	}

	public static void downloadFile(String s, File dest) throws IOException {
		if (dest.exists()) {
			dest.delete();
		}
		FileOutputStream fos = new FileOutputStream(dest);

		HttpURLConnection web = (HttpURLConnection) new URL(s).openConnection();
		web.setRequestProperty("User-Agent", "LabyMod Installer for LabyMod-3");
		ReadableByteChannel channel = Channels.newChannel(web.getInputStream());

		fos.getChannel().transferFrom(channel, 0L, Long.MAX_VALUE);
		fos.close();
	}

	public static String md5(File f) {
		if (!f.exists()) return "";

		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(Files.readAllBytes(f.toPath()));
			byte[] digest = md.digest();
			return DatatypeConverter.printHexBinary(digest).toUpperCase();
		} catch (NoSuchAlgorithmException ex) {
			throw new AssertionError(ex);
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
	}

}