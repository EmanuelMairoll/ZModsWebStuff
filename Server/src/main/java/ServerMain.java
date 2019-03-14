import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

public class ServerMain {

	private static final String DL_LABYMOD_NET_URL = "http://dl.labymod.net/";
	private static final String LABYMOD_REDIRECT_URL = "/labymod/";
	private static final String ADDONS_URL = LABYMOD_REDIRECT_URL + "addons.json";
	private static final String DISABLED_ADDONS_URL = LABYMOD_REDIRECT_URL + "disabled_addons.json";
	private static final String VERSIONS_URL = LABYMOD_REDIRECT_URL + "versions.json";
	private static final String UPDATER_FILE_URL = LABYMOD_REDIRECT_URL + "latest/install/updater.jar";
	private static final String ADDON_ICON_URL = LABYMOD_REDIRECT_URL + "latest/addons/";
	private static final String JAR_FOLDER_URL = LABYMOD_REDIRECT_URL + "latest/";
	private static final String PLAYERMETA_URL = "/playermeta";

	private static HttpServer server;
	private static Logger logger = Logger.getLogger("com.sun.net.httpserver");
	private static JsonRewriter.Addons addonsRewriter = new JsonRewriter.Addons();
	private static JsonRewriter.Versions versionsRewriter = new JsonRewriter.Versions();
	private static HttpContext labymodJarContext;
	private static String lastLabymodJarPath;

	public static void main(String[] args) throws IOException {

		/*
		///////////
		if (FileHelper.addonFolder().exists()) {
			for (File f : FileHelper.addonFolder().listFiles()) {
				f.delete();
			}
			FileHelper.addonFolder().delete();
		}
		///////////
		*/

		DropboxHelper.cloneFolderAsync("/addons", FileHelper.addonFolder());
		DropboxHelper.downloadFileIfExists("/lastStableVersion.txt", new File("lastStableVersion.txt"));

		logger.setLevel(Level.ALL);
		ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new SimpleFormatter());
		handler.setLevel(Level.FINER);
		logger.addHandler(handler);

		try {
			String pString = System.getenv("PORT");
			int port = Integer.parseInt(pString);
			server = HttpServer.create(new InetSocketAddress(port), 0);
		} catch (Exception e) {
			server = HttpServer.create(new InetSocketAddress(80), 0);
		}

		server.createContext(ADDONS_URL, new JsonRewriteHandler("addons.json", addonsRewriter));
		server.createContext(DISABLED_ADDONS_URL, new DisabledAddonsHandler());
		server.createContext(VERSIONS_URL, new JsonRewriteHandler("versions.json", versionsRewriter, ServerMain::updateLabyJarHandler));
		server.createContext(UPDATER_FILE_URL, new JarRewriteHandler("updater"));
		server.createContext(ADDON_ICON_URL, new IconHandler());
		server.createContext(JAR_FOLDER_URL, new DownloadHandler());

		server.createContext(LABYMOD_REDIRECT_URL, new RootHandler());

		server.createContext(PLAYERMETA_URL, new PlayerMetaHandler());

		server.setExecutor(null);
		server.start();

		logger.fine("Server successfully started");

	}

	private static void redirect(HttpExchange t) throws IOException {
		String url = DL_LABYMOD_NET_URL + t.getRequestURI().toString().substring(LABYMOD_REDIRECT_URL.length());
		t.getResponseHeaders().add("Location", url);
		t.sendResponseHeaders(301, -1);
		t.close();
	}

	public static void updateLabyJarHandler() {
		String currentPath = versionsRewriter.getLabyPath();
		if (!currentPath.equals(lastLabymodJarPath)) {
			lastLabymodJarPath = currentPath;

			if (labymodJarContext != null) {
				server.removeContext(labymodJarContext);
			}
			labymodJarContext = server.createContext(LABYMOD_REDIRECT_URL + currentPath, new JarRewriteHandler(null, (f) -> {
				try {
					DropboxHelper.uploadFile(f, "/laby-backups/" + f.getName());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}));
		}
	}

	static class RootHandler implements HttpHandler {
		@Override
		public synchronized void handle(HttpExchange t) throws IOException {
			redirect(t);
		}
	}


	private static class JsonRewriteHandler implements HttpHandler {
		private final String file;
		private final JsonRewriter rewriter;
		private final Runnable onComplete;

		public JsonRewriteHandler(String file, JsonRewriter rewriter) {
			this(file, rewriter, null);
		}

		public JsonRewriteHandler(String file, JsonRewriter rewriter, Runnable onComplete) {
			this.file = file;
			this.rewriter = rewriter;
			this.onComplete = onComplete;
		}

		@Override
		public void handle(HttpExchange clientConnection) throws IOException {
			HttpURLConnection labyConnection = (HttpURLConnection) new URL(DL_LABYMOD_NET_URL + file).openConnection();
			String userAgent = clientConnection.getRequestHeaders().getFirst("User-Agent");
			labyConnection.setRequestProperty("User-Agent", userAgent);
			labyConnection.connect();
			int responseCode = labyConnection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				InputStream is = labyConnection.getInputStream();
				String content = new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
				String modifiedContent = rewriter.rewrite(content);
				clientConnection.sendResponseHeaders(HttpURLConnection.HTTP_OK, modifiedContent.getBytes().length);
				try (OutputStream os = clientConnection.getResponseBody()) {
					os.write(modifiedContent.getBytes());
				}
			} else {
				clientConnection.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, 0);
			}
			clientConnection.close();

			if (onComplete != null) {
				onComplete.run();
			}
		}
	}

	private static class DisabledAddonsHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			t.sendResponseHeaders(HttpURLConnection.HTTP_OK, 2);
			try (OutputStream os = t.getResponseBody()) {
				os.write("[]".getBytes());
			}
			t.close();
		}
	}

	private static class JarRewriteHandler implements HttpHandler {
		private final String generalFilename;
		private final Consumer<File> onComplete;

		JarRewriteHandler(String filename) {
			this(filename, null);
		}

		JarRewriteHandler(String filename, Consumer<File> onComplete) {
			this.generalFilename = filename;
			this.onComplete = onComplete;
		}

		@Override
		public void handle(HttpExchange t) throws IOException {
			String requestUri = t.getRequestURI().toString();

			if (!requestUri.endsWith(".jar")) {
				redirect(t);
				return;
			}

			String filename = generalFilename;

			if (filename == null) {
				filename = requestUri.substring(requestUri.lastIndexOf("/"));
				filename = filename.substring(0, filename.length() - ".jar".length());
			}

			String url = DL_LABYMOD_NET_URL + requestUri.substring(LABYMOD_REDIRECT_URL.length());
			File original = new File(FileHelper.getCacheDir(), filename + "-original.jar");
			File rewritten = new File(FileHelper.getCacheDir(), filename + "-rewritten.jar");

			String oldHash = FileHelper.md5(original);
			FileHelper.downloadFile(url, original);
			if (!oldHash.equals(FileHelper.md5(original)) || !rewritten.exists()) {
				ASMStringReplacer.doPatch(original, rewritten, LabymodDomain.ORIGINAL, LabymodDomain.REPLACEMENT);
			}

			t.sendResponseHeaders(HttpURLConnection.HTTP_OK, rewritten.length());

			OutputStream outputStream = t.getResponseBody();
			Files.copy(rewritten.toPath(), outputStream);
			outputStream.close();

			if (onComplete != null) {
				onComplete.accept(rewritten);
			}
		}
	}

	private static class IconHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {

			String uuid = t.getRequestURI().toString();
			uuid = uuid.substring(ADDON_ICON_URL.length());
			uuid = uuid.substring(0, uuid.length() - "/icon.png".length());

			if (FileHelper.getFileByUUIDString(uuid) != null) {
				InputStream is = ServerMain.class.getResourceAsStream("/assets/zmods.png");
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				byte[] data = new byte[1024];
				int nRead;
				while ((nRead = is.read(data, 0, data.length)) != -1) {
					buffer.write(data, 0, nRead);
				}

				t.sendResponseHeaders(HttpURLConnection.HTTP_OK, buffer.size());
				OutputStream outputStream = t.getResponseBody();
				outputStream.write(buffer.toByteArray());
				outputStream.close();
			} else {
				redirect(t);
			}
		}
	}

	private static class DownloadHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			String uuid = t.getRequestURI().toString();
			uuid = uuid.substring((JAR_FOLDER_URL + "?file=").length());
			uuid = uuid.substring(0, uuid.length() - "&a=1".length());

			File file = FileHelper.getFileByUUIDString(uuid);
			if (file != null) {
				t.getResponseHeaders().add("Content-Disposition", "attachment; filename=file.jar");
				t.sendResponseHeaders(HttpURLConnection.HTTP_OK, file.length());

				OutputStream outputStream = t.getResponseBody();
				Files.copy(file.toPath(), outputStream);
				outputStream.close();
			} else {
				redirect(t);
			}
		}
	}
}
