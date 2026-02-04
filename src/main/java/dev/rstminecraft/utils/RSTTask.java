package dev.rstminecraft.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.PriorityQueue;

import static dev.rstminecraft.RustElytraClient.currentTick;

public class RSTTask {


    /**
     * tasks,储存当前任务,并且按照任务优先级从大到小排序。
     */
    static final PriorityQueue<RSTTask> tasks = new PriorityQueue<>(Comparator.<RSTTask>comparingInt(t -> t.nextRunTick).thenComparingInt(t -> -t.priority));

    public final RSTTask.TaskConsumer action;
    public final int period;
    public final int priority;
    public final Object[] args;
    public int repeatTimes; // -1 = 无限
    public int nextRunTick;

    RSTTask(RSTTask.TaskConsumer action, int period, int repeatTimes, int delay, int priority, Object[] args) {
        this.action = action;
        this.period = Math.max(1, period);
        this.repeatTimes = repeatTimes;
        this.priority = priority;
        this.args = args;
        this.nextRunTick = delay;
    }

    /**
     * 注册任务
     *
     * @param action      要执行的函数 (RSTConsumer)
     * @param period      周期 (tick)
     * @param repeatTimes 执行次数，-1 表示无限
     * @param delay       首次延迟 (tick)
     * @param priority    优先级，数值越大越优先
     * @param args        可变参数，传给 action
     */
    public static @NotNull RSTTask scheduleTask(TaskConsumer action, int period, int repeatTimes, int delay, int priority, Object... args) {
        RSTTask task = new RSTTask(action, period, repeatTimes, delay, priority, args);
        task.nextRunTick = currentTick + delay;
        tasks.add(task);
        return task;
    }

    /**
     * 每tick执行
     * 优先执行优先级大的
     * 若剩余执行次数>0,重新插回队列
     */
    public static void tick() {
        while (!tasks.isEmpty() && tasks.peek().nextRunTick <= currentTick) {
            RSTTask task = tasks.poll();
            task.action.accept(task, task.args);

            if (task.repeatTimes > 0 || task.repeatTimes == -1) {
                if (task.repeatTimes > 0) {
                    task.repeatTimes--;
                }
                task.nextRunTick = currentTick + task.period;
                tasks.add(task);

            }
        }
    }

    /**
     * 任务回调,可使用lambda lambda格式:(self,args)->{}
     */
    public interface TaskConsumer {
        void accept(RSTTask self, Object[] args);
    }
}