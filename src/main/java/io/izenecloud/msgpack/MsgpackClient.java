package io.izenecloud.msgpack;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.msgpack.rpc.Client;
import org.msgpack.rpc.Future;
import org.msgpack.rpc.loop.EventLoop;
import org.msgpack.type.Value;
import org.msgpack.unpacker.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MsgpackClient {
	private static final Logger LOG = LoggerFactory
			.getLogger(MsgpackClient.class);
	private final List<Client> clients;
	private final String collection;

	public MsgpackClient(String urlList, Integer port, String collection) {
		this.collection = collection;

		EventLoop loop = EventLoop.defaultEventLoop();
		clients = new LinkedList<Client>();
		try {
			for (String url : urlList.split(",")) {
				clients.add(new Client(url, port, loop));
			}
		} catch (UnknownHostException e) {
			LOG.info(e.getMessage());
		}
	}

	public MsgpackClient(Configuration conf) {
		this(conf.get("com.b5m.laser.msgpack.host"), conf.getInt(
				"com.b5m.laser.msgpack.port", 0), conf
				.get("com.b5m.laser.collection"));
	}

	public void close() {
		for (Client client : clients) {
			try {
				client.close();
			} catch (Exception e) {
				LOG.debug(e.getMessage());
			}
		}
	}

	public void setTimeout(int requestTimeout) {
		for (Client client : clients) {
			client.setRequestTimeout(requestTimeout);
		}
	}

	public Object asyncRead(Object[] req, String method, Class<?> valueClass)
			throws IOException {
		Value vaule = asyncRead(req, method);
		if (null == vaule) {
			return null;
		}
		Converter converter = new org.msgpack.unpacker.Converter(vaule);
		Object ret = converter.read(valueClass);
		converter.close();
		return ret;
	}

	public Value read(Object[] req, String method) {
		for (Client client : clients) {
			try {
				return client.callApply(method + "|" + collection, req);
			} catch (Exception e) {
				LOG.debug(e.getMessage());
			}
		}
		return null;
	}

	public Value asyncRead(Object[] req, String method) {

		List<Future<Value>> retList = new ArrayList<Future<Value>>(
				clients.size());
		for (Client client : clients) {
			try {
				Future<Value> f = client.callAsyncApply(method + "|"
						+ collection, req);
				retList.add(f);
			} catch (Exception e) {
				LOG.debug(e.getMessage());
			}
		}
		Value ret = null;
		while (null == ret) {
			if (retList.isEmpty()) {
				break;
			}
			for (Future<Value> f : retList) {
				if (f.isDone()) {
					try {
						ret = f.get();
						return ret;
					} catch (Exception e) {
						LOG.debug(e.getMessage());
						retList.remove(f);
					}
				} else if (null != ret) {
					f.cancel(true);
				}
			}
		}
		return null;
	}

	public Object write(Object[] req, String method, Class<?> valueClass)
			throws Exception {
		Value vaule = write(req, method);
		if (null == vaule) {
			return null;
		}
		Converter converter = new org.msgpack.unpacker.Converter(vaule);
		Object ret = converter.read(valueClass);
		converter.close();
		return ret;
	}

	public void writeIgnoreRetValue(Object[] req, String method)
			throws Exception {
		for (Client client : clients) {
			try {
				// client.callApply(method + "|" + collection, req);
				//client.notifyApply(method + "|" + collection, req);
				client.callAsyncApply(method + "|" + collection, req);
			} catch (Exception e) {
				LOG.debug(e.getMessage());
			}
		}
	}

	public Value write(Object[] req, String method) throws Exception {
		Value ret = null;
		List<Future<Value>> retList = new ArrayList<Future<Value>>(
				clients.size());

		for (Client client : clients) {
			try {
				Future<Value> f = client.callAsyncApply(method + "|"
						+ collection, req);
				retList.add(f);
			} catch (Exception e) {
				LOG.debug(e.getMessage());
			}
		}
		for (Future<Value> f : retList) {
			try {
				f.join();
				ret = f.get();
			} catch (Exception e) {
				LOG.debug(e.getMessage());
				throw e;
			}
		}
		return ret;
	}
}
