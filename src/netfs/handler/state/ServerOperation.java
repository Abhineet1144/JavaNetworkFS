package netfs.handler.state;

public class ServerOperation {
    private final long startTime;
    private final String name;

    private double percentComplete;

    public ServerOperation(String name, long startTime, double percentComplete) {
        this.name = name;
        this.startTime = startTime;
        this.percentComplete = percentComplete;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    public String getName() {
        return name;
    }

    public double getPercentComplete() {
        return percentComplete;
    }

    public void setPercentComplete(double percentComplete) {
        this.percentComplete = percentComplete;
    }

    @Override
    public String toString() {
        return "name: " + name + "\ntimetaken: " + getElapsedTime() + "\npercent: " + percentComplete + "\n\n";
    }
}
