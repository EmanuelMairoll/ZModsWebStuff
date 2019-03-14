import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

public interface JsonRewriter {

	String rewrite(String original) throws IOException;

	class Addons implements JsonRewriter {

		@Override
		public String rewrite(String original) throws IOException {
			JsonObject root = (JsonObject) new JsonParser().parse(original);
			JsonArray categories = root.getAsJsonArray("categories");
			categories.add("ZMods");
			root.add("categories", categories);

			JsonObject addons = root.getAsJsonObject("addons");
			JsonArray v18 = addons.getAsJsonArray("18");

			int sortingIndex = 0;
			for (File addon : FileHelper.listAddonFiles()) {
				JsonObject addonJson = FileHelper.addonJsonForJarFile(new JarFile(addon));
				addonJson.addProperty("category", categories.size() + "");
				addonJson.addProperty("enabled", false);
				addonJson.addProperty("restart", false);
				addonJson.addProperty("filesize", 0);
				addonJson.addProperty("hash", FileHelper.md5(addon));
				addonJson.addProperty("includeInJar", false);
				addonJson.addProperty("mcversion", "18");
				JsonArray sorting = new JsonArray();
				if (addonJson.has("showFirst") && addonJson.getAsJsonPrimitive("showFirst").getAsBoolean()) {
					sorting.add(sortingIndex);
					sorting.add(10000 + sortingIndex);
					sorting.add(10000 + sortingIndex);
					sorting.add(10000 + sortingIndex);
					sorting.add(sortingIndex - 2000);
				} else {
					sorting.add(11000 + sortingIndex);
					sorting.add(11000 + sortingIndex);
					sorting.add(11000 + sortingIndex);
					sorting.add(11000 + sortingIndex);
					sorting.add(sortingIndex - 1000);
				}
				sortingIndex++;
				addonJson.add("sorting", sorting);
				addonJson.addProperty("verified", true);

				v18.add(addonJson);
			}

			addons.add("18", v18);
			root.add("addons", addons);

			return root.toString();
		}
	}

	class Versions implements JsonRewriter {
		private String labyPath = null;

		@Override
		public String rewrite(String original) throws IOException {
			String lastStableVersion = FileHelper.getLastStableVersion();

			JsonObject root = (JsonObject) new JsonParser().parse(original);
			JsonObject v189 = root.getAsJsonObject("1.8.9");
			String version = v189.getAsJsonPrimitive("version").getAsString();

			String url = v189.getAsJsonPrimitive("url").getAsString();
			labyPath = url.substring(url.indexOf(LabymodDomain.ORIGINAL) + LabymodDomain.ORIGINAL.length() + 1);

			if (lastStableVersion != null && !version.equals(lastStableVersion)) {
				v189.addProperty("version", lastStableVersion);
				v189.remove("url");
			} else {
				v189.addProperty("url", url.replace(LabymodDomain.ORIGINAL, LabymodDomain.REPLACEMENT));
			}
			root.add("1.8.9", v189);

			return root.toString();
		}

		public String getLabyPath() {
			return labyPath;
		}
	}
}
