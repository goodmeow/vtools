package com.omarea.shell

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.support.v4.content.PermissionChecker
import com.omarea.shared.Consts
import com.omarea.shared.SpfConfig
import com.omarea.ui.ProgressBarDialog
import com.omarea.vboot.R

/**
 * 检查获取root权限
 * Created by helloklf on 2017/6/3.
 */

class CheckRootStatus(var context: Context, private var next:Runnable? = null, private var skip:Runnable?, private var disableSeLinux: Boolean = false) {
    var myHandler: Handler = Handler()

    private fun checkPermission(permission: String): Boolean =
            PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

    //是否已经Root
    private fun isRoot(disableSeLinux: Boolean): Boolean {
        var process: java.lang.Process? = null
        try {
            process = Runtime.getRuntime().exec("su")
            val out = process!!.outputStream.bufferedWriter()
            if(disableSeLinux)
                out.write(Consts.DisableSELinux)
            out.write("dumpsys deviceidle whitelist +com.omarea.vboot;\n")
            if (!(checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) && checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
                out.write("pm grant com.omarea.vboot android.permission.READ_EXTERNAL_STORAGE;\n")
                out.write("pm grant com.omarea.vboot android.permission.WRITE_EXTERNAL_STORAGE;\n")
                out.write("pm grant com.omarea.vboot android.permission.SYSTEM_ALERT_WINDOW;\n")
            }
            out.write("exit;\n")
            out.write("exit;\n")
            out.flush()

            process.waitFor()
            val r = process.exitValue() == 0
            process.destroy()
            return r
            //if (msg == "permission denied" || msg.contains("not allowed") || msg == "not found")
        } catch (e: Exception) {
            if (process != null)
                process.destroy()
            e.stackTrace
            return false
        }

    }


    var therad: Thread? = null
    fun forceGetRoot() {
        val pd = ProgressBarDialog(context)
        pd.showDialog("正在检查ROOT权限")
        var completed = false
        therad = Thread {
            if (!isRoot(disableSeLinux)) {
                completed = true
                myHandler.post {
                    pd.hideDialog()
                    val alert = AlertDialog.Builder(context)
                    alert.setCancelable(false)
                    alert.setTitle(R.string.error_root)
                    alert.setNegativeButton(R.string.btn_refresh, { _, _ ->
                        if(therad != null && therad!!.isAlive && !therad!!.isInterrupted) {
                            therad!!.interrupt()
                            therad = null
                        }
                        forceGetRoot()
                    })
                    alert.setNeutralButton(R.string.btn_skip, { _, _ ->
                        //android.os.Process.killProcess(android.os.Process.myPid())
                        completed = true
                        if(therad != null && therad!!.isAlive && !therad!!.isInterrupted) {
                            therad!!.interrupt()
                            therad = null
                        }
                        myHandler.post {
                            pd.hideDialog()
                            if (skip != null)
                                skip!!.run()
                        }
                    })
                    alert.create().show()
                }
            } else {
                completed = true
                myHandler.post {
                    pd.hideDialog()
                    if (next != null)
                        next!!.run()
                }
            }
        };
        therad!!.start()
        myHandler.postDelayed({
            if (!completed) {
                pd.hideDialog()
                val alert = AlertDialog.Builder(context)
                alert.setCancelable(false)
                alert.setTitle(R.string.error_root)
                alert.setMessage(R.string.error_su_timeout)
                alert.setNegativeButton(R.string.btn_refresh, { _, _ ->
                    if(therad != null && therad!!.isAlive && !therad!!.isInterrupted) {
                        therad!!.interrupt()
                        therad = null
                    }
                    forceGetRoot()
                })
                alert.setNeutralButton(R.string.btn_skip, { _, _ ->
                    if(therad != null && therad!!.isAlive && !therad!!.isInterrupted) {
                        therad!!.interrupt()
                        therad = null
                    }
                    completed = true
                    myHandler.post {
                        pd.hideDialog()
                        if (skip != null)
                            skip!!.run()
                    }
                    //android.os.Process.killProcess(android.os.Process.myPid())
                })
                alert.create().show()
            }
        }, 10000)
    }


    companion object {
        public fun isMagisk(): Boolean {
            return SysUtils.executeCommandWithOutput(false, "su -v").contains("MAGISKSU")
        }

        public fun isTmpfs(dir: String): Boolean {
            return  SysUtils.executeCommandWithOutput(false, " df | grep tmpfs | grep \"$dir\"").trim().length > 0
        }
    }
}
