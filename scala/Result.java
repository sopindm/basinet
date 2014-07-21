package basinet;

public class Result {
    public static Result create(boolean isUnderflow, boolean isOverflow) {
        if(isUnderflow && isOverflow) return UNDERFLOW_OVERFLOW;
        if(isUnderflow && !isOverflow) return UNDERFLOW;
        if(!isUnderflow && isOverflow) return OVERFLOW;

        return NOTHING;
    }

    private boolean isUnderflow;
    private boolean isOverflow;

    private Result(boolean isUnderflow, boolean isOverflow) {
        this.isUnderflow = isUnderflow;
        this.isOverflow = isOverflow;
    }

    public boolean isOverflow() { return isOverflow; }
    public boolean isUnderflow() { return isUnderflow; }

    public Result underflow(boolean is) { return create(is, isOverflow); }
    public Result overflow(boolean is) { return create(isUnderflow, is); }

    public Result merge(Result result) { return create(isUnderflow || result.isUnderflow,
                                                       isOverflow || result.isOverflow); }

    public static Result NOTHING = new Result(false, false);
    public static Result OVERFLOW = new Result(false, true);
    public static Result UNDERFLOW = new Result(true, false);
    public static Result UNDERFLOW_OVERFLOW = new Result(true, true);

    @Override
    public String toString() {
        String status = "";
            
        if(isUnderflow) status += " UNDERFLOW";
        if(isOverflow) status += " OVERFLOW";

        if(status.equals(""))
            status = "NOTHING";

        return status;
    }
}
