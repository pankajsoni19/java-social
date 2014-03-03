Anudi
=

This project was based on my curosity and partly other to gather code samples into a single place. Its has support for

1. Multiple sellers and buyers
2. Chat within the community or domain
3. Pure REST based smtp client (yes, it is actually stateless.)
4. Files are stored in uuid/uuid/uuid/ based directory structure. 
5. chat msgs, are in cql v3 style 
6. the db store is cassandra
7. the server model is event based, nothing occurs without a trigger.
8. lots of encryption. client-client, client-server, server-server...

Note
=
1. Nate's impl of websocks is good. I would be running some tests.
2. The settings are in conf folder and it uses logger to log comments.
3. To run do include conf/properties in classpath
4. eclipse has a kill button, no graceful shutdown so export RUNNING_IN_ECLIPSE or which ever id you use, and we can have a graceful shutdown.

TODO
=
1. The chat protocol is not clear, as it is a web-socket, so being a continuos stream, I am deciding between cql style data format or something.
2. Will be moving file storage to indexed distributed directory structure.
