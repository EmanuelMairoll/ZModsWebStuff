import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerMetaHandler implements HttpHandler {

	private final Map<String, Map<String, String>> playerMeta = new HashMap<>();
	private final Map<String, Long> lastPush = new HashMap<>();

	public PlayerMetaHandler() {
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				Iterator<Map.Entry<String, Long>> i = lastPush.entrySet().iterator();

				while (i.hasNext()) {

					Map.Entry<String, Long> next = i.next();

					if (next.getValue() + 100000 < System.currentTimeMillis()) {
						i.remove();
						playerMeta.remove(next.getKey());

					}

				}

			}
		}, 0, 1000);
	}

	@Override
	public void handle(HttpExchange h) throws IOException {
		switch (h.getRequestMethod()) {
			case "GET": {
				String clientID = h.getRequestURI().toString().substring("/playermeta".length());
				if (clientID.startsWith("/")) {
					clientID = clientID.substring(1);
				}

				Map<String, String> properties = playerMeta.get(clientID);

				if (clientID.equals("") || clientID.contains("/") || properties == null) {
					h.sendResponseHeaders(404, 0);
				} else {
					String toSend =
							properties.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(System.lineSeparator()));
					byte[] data = toSend.getBytes();

					h.sendResponseHeaders(200, data.length);
					h.getResponseBody().write(data);
				}
				h.close();
			}
			break;
			case "POST": {
				List<String> clientIDList = h.getRequestHeaders().get("ClientID");
				if (clientIDList != null && clientIDList.size() > 0) {
					String clientID = clientIDList.get(0);
					lastPush.put(clientID, System.currentTimeMillis());

					playerMeta.putIfAbsent(clientID, new HashMap<>());
					Map<String, String> properties = playerMeta.get(clientID);

					try (BufferedReader br = new BufferedReader(new InputStreamReader(h.getRequestBody()))) {
						br.lines().forEach(l -> {
							String[] parts = l.split("=");
							if (parts.length == 2) {
								properties.put(parts[0], parts[1]);
							}
						});
					}
					h.sendResponseHeaders(200, 0);
				} else {
					h.sendResponseHeaders(400, 0);
				}

				h.close();
			}
			break;
		}
	}

}
