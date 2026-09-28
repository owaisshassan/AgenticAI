package part3;

public class Chunk {
    private final String id;
    private final String sourceFile;
    private final String text;
    private double[] embedding;

    public Chunk(String id, String sourceFile, String text) {
        this.id = id;
        this.sourceFile = sourceFile;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getText() {
        return text;
    }

    public double[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(double[] embedding) {
        this.embedding = embedding;
    }

    @Override
    public String toString() {
        return "Chunk{id='" + id + "', sourceFile='" + sourceFile + "', textLength="
                + (text == null ? 0 : text.length()) + "}";
    }
}
