package com.googlecode.utterlyidle.s3;

import com.googlecode.totallylazy.Function1;
import com.googlecode.totallylazy.Group;
import com.googlecode.totallylazy.Option;
import com.googlecode.totallylazy.Pair;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.totallylazy.Strings;
import com.googlecode.utterlyidle.HttpHeaders;
import com.googlecode.utterlyidle.Request;

import java.util.ArrayList;
import java.util.List;

import static com.googlecode.totallylazy.Callables.first;
import static com.googlecode.totallylazy.Callables.second;
import static com.googlecode.totallylazy.Pair.functions.pairToString;
import static com.googlecode.totallylazy.Pair.functions.replaceFirst;
import static com.googlecode.totallylazy.Pair.pair;
import static com.googlecode.totallylazy.Predicates.in;
import static com.googlecode.totallylazy.Predicates.not;
import static com.googlecode.totallylazy.Predicates.nullValue;
import static com.googlecode.totallylazy.Predicates.where;
import static com.googlecode.totallylazy.Sequences.sequence;
import static com.googlecode.totallylazy.Strings.blank;
import static com.googlecode.totallylazy.Strings.isBlank;
import static com.googlecode.totallylazy.Strings.startsWith;
import static com.googlecode.totallylazy.Strings.toLowerCase;
import static com.googlecode.totallylazy.UrlEncodedMessage.decode;
import static com.googlecode.totallylazy.UrlEncodedMessage.encode;
import static com.googlecode.utterlyidle.HttpHeaders.CONTENT_TYPE;
import static com.googlecode.utterlyidle.HttpHeaders.Content_MD5;
import static com.googlecode.utterlyidle.HttpHeaders.HOST;
import static java.lang.String.format;
import static java.util.regex.Pattern.quote;

public class S3RequestStringifier {
    public static final String s3 = "s3.amazonaws.com";
    public static final String x_amz_date = "x-amz-date";
    // see http://docs.aws.amazon.com/AmazonS3/latest/dev/RESTAuthentication.html#d0e3773
    private final Sequence<String> canonicalResourceQueryParams = sequence("acl", "lifecycle", "location", "logging", "notification", "partNumber", "policy", "requestPayment", "torrent", "uploadId", "uploads", "versionId", "versioning", "versions", "website");

    public String stringToSign(Request request) {
        return format("%s\n%s\n%s\n%s\n%s%s",
                request.method(),
                request.headers().valueOption(Content_MD5).getOrElse(""),
                request.headers().valueOption(CONTENT_TYPE).getOrElse(""),
                date(request),
                canonicalizedAmzHeaders(request),
                canonicalizedResource(request));
    }

    private String canonicalizedAmzHeaders(final Request request) {
        String result = sequence(request.headers()).
                map(replaceFirst(toLowerCase(), String.class)).
                filter(where(first(String.class), startsWith("x-amz-"))).
                filter(where(first(String.class), not(x_amz_date))).
                groupBy(first(String.class)).
                map(mergeHeaders()).
                sortBy(first(String.class)).
                map(pairToString("", ":", "")).
                toString("", "\n", "");
        return isBlank(result)
                ? result
                : result + "\n";
    }

    private Function1<Group<String, Pair<String, String>>, Pair<String, String>> mergeHeaders() {
        return new Function1<Group<String, Pair<String, String>>, Pair<String, String>>() {
            @Override
            public Pair<String, String> call(final Group<String, Pair<String, String>> headers) throws Exception {
                String commaSeparatedValues = headers.map(second(String.class)).toString(",");
                return pair(headers.key(), commaSeparatedValues);
            }
        };
    }

    private String date(Request request) {
        return sequence(
                request.headers().getValue(x_amz_date),
                request.headers().getValue(HttpHeaders.DATE))
                .find(not(nullValue()))
                .getOrThrow(new RuntimeException(format("No Date or %s header in request:\n%s", x_amz_date, request)));
    }

    private String canonicalizedResource(Request request) {
        // See http://docs.aws.amazon.com/AmazonS3/latest/dev/RESTAuthentication.html
        return sequence(
                virtualHostBucket(request),
                request.uri().path(),
                subresource(request)
        ).toString("");
    }

    private String subresource(final Request request) {
        return canonicalizedQuery(
                sequence(parse(request.uri().query()))
                        .filter(where(first(String.class), in(canonicalResourceQueryParams)))
                        .sortBy(first(String.class)));
    }

    private String virtualHostBucket(final Request request) {
        Option<String> bucket = sequence(
                request.uri().authority(),
                hostHeaderAuthority(request))
                .find(not(s3).and(not(blank())));
        return bucket.isEmpty()
                ? ""
                : "/" + bucket.get().split(quote("." + s3))[0];
    }

    public static String hostHeaderAuthority(final Request request) {
        Option<String> header = request.headers().valueOption(HOST);
        return header.isEmpty()
                ? null
                : header.get().split(":")[0];
    }

    // Identical to UrlEncodedMessage.toString(), except:
    //
    // pair("param", "")
    // ?param=
    //
    // whereas:
    //
    // pair("param", null)
    // ?param
    //
    // so that query params are fully reversible
    private static String canonicalizedQuery(Iterable<? extends Pair<String, String>> pairs) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Pair<String, String> pair : pairs) {
            if (first) {
                first = false;
                builder.append("?");
            } else {
                builder.append("&");
            }
            builder.append(encode(pair.first()));
            if (pair.second() != null)
                builder.append("=").append(encode(pair.second()));
        }
        return builder.toString();
    }

    // Identical to UrlEncodedMessage.parse(), except:
    //
    // ?param=
    // pair("param", "")
    //
    // whereas:
    //
    // ?param
    // pair("param", null)
    //
    // so that query params are fully reversible
    private static List<Pair<String, String>> parse(String value) {
        List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();
        if (Strings.isEmpty(value)) {
            return result;
        }

        for (String pair : value.split("&")) {
            String[] nameValue = pair.split("=");
            if (nameValue.length == 1) {
                result.add(pair(decode(nameValue[0]), (String) null));
            }
            if (nameValue.length == 2) {
                result.add(pair(decode(nameValue[0]), decode(nameValue[1])));
            }
        }
        return result;
    }
}
