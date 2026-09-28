package Lab5;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;

/**
 * Uploads this run's trace file (and, later, an agent manifest) to S3 -
 * the hand-off point for a Bedrock Agent to pick this bundle up. Kept as
 * its own tool, separate from AgentTrace itself, so trace-writing (local,
 * always happens) and trace-upload (remote, optional, needs AWS
 * credentials/network) can be tested independently. Not exercised by the
 * default test suite - see S3ArtifactUploaderTest for why.
 */
public class S3ArtifactUploader implements Tool {

    private final S3Client s3Client;
    private final String bucket;

    public S3ArtifactUploader(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public String name() {
        return "s3_artifact_uploader";
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    public record UploadResult(String bucket, String key, String eTag) {
    }

    public UploadResult uploadTrace(Path traceFile, String keyPrefix) {
        String key = keyPrefix + "/" + traceFile.getFileName();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/x-ndjson")
                .build();

        var response = s3Client.putObject(request, RequestBody.fromFile(traceFile));
        return new UploadResult(bucket, key, response.eTag());
    }
}
