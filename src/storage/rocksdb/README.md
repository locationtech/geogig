#RocksDB Storage Backend

This is the storage plugin for rocksdb <rocksdb.org>

RocksDB is an embeddable persistent key-value store for fast storage written in C++

The maven JNI bindings are obtained from Maven central at https://mvnrepository.com/artifact/org.rocksdb/rocksdbjni

#Usage

To create a repository with the rocksdb storage backend (objects, graph, blobs, and conflicts) use the following command:

```
gig init --config "rocksdb.version=1,storage.objects=rocksdb,storage.graph=rocksdb,storage.refs=file"
```