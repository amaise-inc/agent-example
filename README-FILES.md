# File Transfer API

The amaise SDK uses presigned URLs to upload and download files directly to AWS for efficiency and stability.

Based on the data location of the tenant, the presigned URLs will point eihter to `https://data.amaise.com/` (Germany), `https://data-ch.amaise.com/` (Switzerland) or `https://data-us.amaise.com` (United States).

## Flow for File Download

**The SDK requests a presigned URL from the amaise files API. HTTP 307 with the presigned URL in the location header is returned.**

```
REQUEST: https://agents.eu.amaise.com/agents/v1/files/556a0908-0dcf-49f9-ab34-898d7f3a6212/EXPORT/b479a684-db08-4f34-8f11-5c28fdff157c.pdf GET
	:authority: agents-suffix.amaise.com
	:method: GET
	:path: /agents/v1/files/556a0908-0dcf-49f9-ab34-898d7f3a6212/EXPORT/b479a684-db08-4f34-8f11-5c28fdff157c.pdf
	:scheme: https
	User-Agent: Java-http-client/21
	Accept: */*
	Authorization: Bearer <TOKEN>

RESPONSE HEADERS:
	:status: 307
	accept-ranges: bytes
	cache-control: no-cache, no-store, max-age=0, must-revalidate
	content-length: 0
	date: Fri, 16 Sep 2022 13:48:01 GMT
	expires: 0
	location: https://data.eu.amaise.com/526602b4-0e96-4c90-bc28-ce720c9c6521/556a0908-0dcf-49f9-ab34-898d7f3a6212/export/b479a684-db08-4f34-8f11-5c28fdff157c.pdf?Expires=1663336111&Signature=xxx~kpRbLyFT8uIPQXsJf3jEu-W0xT8dtwf~6xN4Yt27GY4kxS-GljaumncWjrvKaekXUAsmCF-9meaSq4mjbBN4RvRyFe8UisoZfsl4N27h21I~PMdjCu2oMwnRUUZRGtwVWgrzptL34i-rcFFnQPf7FuN7CMN0Mz4RDpl4~-NpftOsUpVL50fvzMh5P948bdfGqewsZCWUBuaBNNRl3O2mgBjWhx9I9Jr4fke3Ze75NUElIKXRDvwTrBbXvpEiyOqBjJv1tXsXa5l5mdr775NWjM7oyCA5A94h6u1oCUtlf~kTjMyyDlLn6ARd2Ems-mK1Gt~uW73PF8lFMDAQ__&Key-Pair-Id=KQ99OHURCHWVL
	pragma: no-cache
	strict-transport-security: max-age=31536000 ; includeSubDomains
	vary: Origin
	vary: Access-Control-Request-Method
	vary: Access-Control-Request-Headers
	x-content-type-options: nosniff
	x-frame-options: DENY
	x-xss-protection: 1; mode=block
```

**SDK Request the binary file from the response's location header.**

```
REQUEST: https://data.eu.amaise.com/526602b4-0e96-4c90-bc28-ce720c9c6521/556a0908-0dcf-49f9-ab34-898d7f3a6212/export/b479a684-db08-4f34-8f11-5c28fdff157c.pdf?Expires=1663336111&Signature=xxx~kpRbLyFT8uIPQXsJf3jEu-W0xT8dtwf~6xN4Yt27GY4kxS-GljaumncWjrvKaekXUAsmCF-9meaSq4mjbBN4RvRyFe8UisoZfsl4N27h21I~PMdjCu2oMwnRUUZRGtwVWgrzptL34i-rcFFnQPf7FuN7CMN0Mz4RDpl4~-NpftOsUpVL50fvzMh5P948bdfGqewsZCWUBuaBNNRl3O2mgBjWhx9I9Jr4fke3Ze75NUElIKXRDvwTrBbXvpEiyOqBjJv1tXsXa5l5mdr775NWjM7oyCA5A94h6u1oCUtlf~kTjMyyDlLn6ARd2Ems-mK1Gt~uW73PF8lFMDAQ__&Key-Pair-Id=KQ99OHURCHWVL GET
	:authority: data.eu.amaise.com
	:method: GET
	:path: /526602b4-0e96-4c90-bc28-ce720c9c6521/556a0908-0dcf-49f9-ab34-898d7f3a6212/export/b479a684-db08-4f34-8f11-5c28fdff157c.pdf?Expires=1663336111&Signature=sraMO~kpRbLyFT8uIPQXsJf3jEu-W0xT8dtwf~6xN4Yt27GY4kxS-GljaumncWjrvKaekXUAsmCF-9meaSq4mjbBN4RvRyFe8UisoZfsl4N27h21I~PMdjCu2oMwnRUUZRGtwVWgrzptL34i-rcFFnQPf7FuN7CMN0Mz4RDpl4~-NpftOsUpVL50fvzMh5P948bdfGqewsZCWUBuaBNNRl3O2mgBjWhx9I9Jr4fke3Ze75NUElIKXRDvwTrBbXvpEiyOqBjJv1tXsXa5l5mdr775NWjM7oyCA5A94h6u1oCUtlf~kTjMyyDlLn6ARd2Ems-mK1Gt~uW73PF8lFMDAQ__&Key-Pair-Id=KQ99OHURCHWVL
	:scheme: https
	User-Agent: Java-http-client/21

RESPONSE HEADERS:
	:status: 200
	accept-ranges: bytes
	alt-svc: h3=":443"; ma=86400
	content-length: 556407
	content-type: application/octet-stream
	date: Fri, 16 Sep 2022 13:48:02 GMT
	etag: "b8ac24a66a92d4ea2694a5b3107236e0"
	last-modified: Fri, 16 Sep 2022 13:47:51 GMT
	server: AmazonS3
	via: 1.1 f0f5607a03d2ae4c43b553dc2cef0c9e.cloudfront.net (CloudFront)
	x-amz-cf-id: gn6u-YToYh1_YpuwGfOxG9rpYuVVFRCqYx7wp--ySHRRW6Y-Q-Rm5A==
	x-amz-cf-pop: ZRH50-C1
	x-amz-server-side-encryption: aws:kms
	x-amz-server-side-encryption-aws-kms-key-id: arn:aws:kms:eu-central-1:526911608169:key/d3955439-6cb1-4986-85ec-87af04315a85
	x-amz-server-side-encryption-bucket-key-enabled: true
	x-amz-version-id: 5n6rCHkeZYFJBpTXjww.R5c2ASOuzY6r
	x-cache: Miss from cloudfront

```

## Flow for File Upload

**SDK PUTs the binary to the presigned URL pointing to CloudFront, located at https://upload.amaise.com**

```
REQUEST: https://upload.eu.amaise.com/526602b4-0e96-4c90-bc28-ce720c9c6521/5205b0f6-cc86-4cae-b2b2-76efbe27725e?Expires=1663335989&Signature=xxx~7XqfHC2Ebi1uLkiSN8NtO8fT~9g-OVH3DYV9nS6nQz-v~vNFetVWiO~b3zOnaWM1uds97UnsCwOZl-uboH13SpMeoRh~TWIC~mD6eW0KJAhZSNLulVUSledjei9RA4ZoMbSGJs-hPSq~weYBlXThG-8GvNtOlXFHsv2FH5M8M2NVn7LNr5Y3kVUUSRgNZRFadQzzetRcbk1iQtUffR-4ZSSRm2LnGLTMdCj-~Q~D7e1AeeqaPGC6W9bd4goUTi3AJ737MC3v2ZuAucCn-LKAh4tRP~YicyFmZOrmgOaBMyH9euRCHuDrJz4sGfsKYEwSZS2omzeBMtF9cXxHm3IA__&Key-Pair-Id=KQ99OHURCHWVL PUT
	:authority: upload.eu.amaise.com
	:method: PUT
	:path: /526602b4-0e96-4c90-bc28-ce720c9c6521/5205b0f6-cc86-4cae-b2b2-76efbe27725e?Expires=1663335989&Signature=xxx~9g-OVH3DYV9nS6nQz-v~vNFetVWiO~b3zOnaWM1uds97UnsCwOZl-uboH13SpMeoRh~TWIC~mD6eW0KJAhZSNLulVUSledjei9RA4ZoMbSGJs-hPSq~weYBlXThG-8GvNtOlXFHsv2FH5M8M2NVn7LNr5Y3kVUUSRgNZRFadQzzetRcbk1iQtUffR-4ZSSRm2LnGLTMdCj-~Q~D7e1AeeqaPGC6W9bd4goUTi3AJ737MC3v2ZuAucCn-LKAh4tRP~YicyFmZOrmgOaBMyH9euRCHuDrJz4sGfsKYEwSZS2omzeBMtF9cXxHm3IA__&Key-Pair-Id=KQ99OHURCHWVL
	:scheme: https
	content-length: 11540
	User-Agent: Java-http-client/21
	x-amz-acl: bucket-owner-full-control

HEADERS: RESPONSE HEADERS:
	:status: 200
	alt-svc: h3=":443"; ma=86400
	content-length: 0
	date: Fri, 16 Sep 2022 13:41:30 GMT
	etag: "e11d1bfdd8f7087d3547e01f899e288e"
	server: AmazonS3
	via: 1.1 871dedfc10f4428aa2412b6f788b791a.cloudfront.net (CloudFront)
	x-amz-cf-id: 01BYXU2Z4SpuVkazWgFuQ02K72lEILhxTTljBR_PDve7X6MP8QvnxQ==
	x-amz-cf-pop: ZRH50-C1
	x-amz-expiration: expiry-date="Mon, 19 Sep 2022 00:00:00 GMT", rule-id="delete-one-week-old-objects"
	x-amz-server-side-encryption: AES256
	x-cache: Miss from cloudfront
```
