@file:Suppress("JoinDeclarationAndAssignment", "LiftReturnOrAssignment")

package apps.chocolatecakecodes.bluebeats.util

import android.util.Log
import java.util.*
import kotlin.random.Random

private typealias TaskRunnable = () -> Long

/**
 * This class provides functions similar to setTimeout() and setInterval() in JavaScript. <br/>
 * Additionally every task can change its interval after every execution: 0 => keep last interval, > 0 => set new Interval, < 0 => cancel task
 */
class TimerThread private constructor(){

    companion object{

        val INSTANCE = TimerThread()

        private const val LOG_TAG = "TimerThread"
    }

    private val lock = Object()
    private val idRng = Random.Default
    private val taskQueue: PriorityQueue<Task>
    private val runner: Runner

    init{
        taskQueue = PriorityQueue(kotlin.Comparator{ o1, o2 ->
            if(o1.sleepLeft() < o2.sleepLeft())
                return@Comparator -1
            if(o1.sleepLeft() > o2.sleepLeft())
                return@Comparator 1
            return@Comparator 0
        })

        runner = Runner()
        runner.start()
    }

    fun addTimeout(task: TaskRunnable, timeout: Long): Int{
        synchronized(lock){
            val idTimePart: Long = System.currentTimeMillis()
            val idRngPart: Long = idRng.nextLong()
            val id = ((idTimePart and 0x000000FF) or (idRngPart and 0xFFFFFF00)).toInt()

            taskQueue.offer(Task(task, timeout, false, id))
            lock.notifyAll()

            return id
        }
    }

    fun addInterval(task: TaskRunnable, timeout: Long): Int{
        synchronized(lock){
            val idTimePart: Long = System.currentTimeMillis()
            val idRngPart: Long = idRng.nextLong()
            val id = ((idTimePart and 0x000000FF) or (idRngPart and 0xFFFFFF00)).toInt()

            taskQueue.offer(Task(task, timeout, true, id))
            lock.notifyAll()

            return id
        }
    }

    fun removeTask(id: Int){
        synchronized(lock){
            taskQueue.removeIf { it.id == id }
        }
    }

    fun destroy(){
        runner.alive = false
        runner.interrupt()
        taskQueue.clear()
    }

    private class Task(private val task: TaskRunnable, private var interval: Long, val repeating: Boolean, val id: Int) {

        private var lastExecution: Long

        init{
            lastExecution = System.currentTimeMillis()
        }

        fun execute(){
            try{
                val nextInterval = task()
                if(nextInterval != 0L)
                    interval = nextInterval
            }catch (e: Exception){
                Log.e(LOG_TAG, "task threw uncaught exception", e)
            }finally {
                lastExecution = System.currentTimeMillis()
            }
        }

        fun sleepLeft(): Long{
            val ret = (lastExecution + interval) - System.currentTimeMillis()
            return if(ret >= 0) ret else 0
        }

        fun shouldCancel(): Boolean{
            return interval < 0
        }
    }

    private inner class Runner : Thread(){

        @Volatile
        var alive = true

        init{
            this.name = "TimerThread-Runner"
            this.isDaemon = true
        }

        override fun run() {
            while (alive) {
                try {
                    // wait until tasks are added
                    synchronized(lock) {
                        while (taskQueue.isEmpty()) {
                            lock.wait()
                        }
                    }

                    // wait until first task should be executed
                    val firstTaskPeek: Task?
                    synchronized(lock){
                        firstTaskPeek = taskQueue.peek()
                    }
                    if (firstTaskPeek === null)
                        continue
                    synchronized(lock) {
                        lock.wait(firstTaskPeek.sleepLeft())
                    }

                    // check if task should be executed and execute it
                    val task : Task?
                    synchronized(lock){
                        task = taskQueue.poll()
                    }
                    if(task === null)
                        continue
                    if(task.sleepLeft() > 0){
                        // not to be executed now
                        synchronized(lock) {
                            taskQueue.offer(task)
                        }
                        continue
                    }
                    task.execute()
                    if(task.repeating && !task.shouldCancel()) {// reschedule if it is an interval task
                        synchronized(lock) {
                            taskQueue.offer(task)
                        }
                    }
                } catch (e: InterruptedException){
                    continue
                }
            }
        }
    }
}
