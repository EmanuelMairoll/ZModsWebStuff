import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.WriteMode;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DropboxHelper {

	private static final String ACCESS_TOKEN = Secret.DROPBOX_TOKEN;
	private static final DbxClientV2 client = new DbxClientV2(new DbxRequestConfig("dropbox/zmods"), ACCESS_TOKEN);

	public static void cloneFolderAsync(String dropboxFolder, File localFolder) throws IOException {
		localFolder.mkdirs();

		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			ListFolderResult result = client.files().listFolder(dropboxFolder);
			while (true) {
				for (Metadata metadata : result.getEntries()) {
					executor.submit(() -> {
						try {
							String filename = metadata.getPathDisplay().substring(metadata.getPathDisplay().lastIndexOf("/"));
							downloadFile(metadata.getPathDisplay(), new File(localFolder, filename));
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				}

				if (!result.getHasMore()) {
					executor.shutdown();
					try {
						executor.awaitTermination(1, TimeUnit.MINUTES);
					} catch (InterruptedException ignored) {
					}
					break;
				}

				result = client.files().listFolderContinue(result.getCursor());
			}
		} catch (DbxException e) {
			e.printStackTrace();
		}
	}

	public static void cloneFolder(String dropboxFolder, File localFolder) throws IOException {
		localFolder.mkdirs();

		try {
			ListFolderResult result = client.files().listFolder(dropboxFolder);
			while (true) {
				for (Metadata metadata : result.getEntries()) {
					String filename = metadata.getPathDisplay().substring(metadata.getPathDisplay().lastIndexOf("/"));
					downloadFile(metadata.getPathDisplay(), new File(localFolder, filename));
				}
				if (!result.getHasMore()) {
					break;
				}
				result = client.files().listFolderContinue(result.getCursor());
			}
		} catch (DbxException e) {
			e.printStackTrace();
		}
	}

	public static void downloadFile(String dropboxPath, File localFile) throws IOException {
		try (OutputStream out = new FileOutputStream(localFile)) {
			client.files().downloadBuilder(dropboxPath).download(out);
		} catch (DbxException e) {
			e.printStackTrace();
		}
	}

	public static void downloadFileIfExists(String dropboxPath, File localFile) throws IOException {
		try {
			client.files().getMetadata(dropboxPath);
			downloadFile(dropboxPath, localFile);
		} catch (GetMetadataErrorException e) {
			if (!e.errorValue.isPath() || !e.errorValue.getPathValue().isNotFound()) {
				e.printStackTrace();
			}
		} catch (DbxException e) {
			e.printStackTrace();
		}
	}

	public static void uploadFile(File localFile, String dropboxPath) throws IOException {
		try (InputStream in = new FileInputStream(localFile)) {
			client.files().uploadBuilder(dropboxPath).withMode(WriteMode.OVERWRITE).uploadAndFinish(in);
		} catch (DbxException e) {
			e.printStackTrace();
		}
	}

}
