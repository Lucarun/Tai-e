package pascal.taie.analysis.pta.plugin.taint;

import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

class InferredTaintTransfer extends TaintTransfer {

    private int weight;

    public InferredTaintTransfer(JMethod method,
                                 TransferPoint from,
                                 TransferPoint to,
                                 Type type,
                                 int weight) {
        super(method, from, to, type);
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    @Override
    public String toString() {
        return "InferredTaintTransfer{ " + super.toString() + " }";
    }
}
