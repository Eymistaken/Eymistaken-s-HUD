import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import java.lang.reflect.RecordComponent;

public class MethodDumper {
    public static void main(String[] args) {
        System.out.println("--- Click ---");
        for (RecordComponent rc : Click.class.getRecordComponents()) {
            System.out.println("Click: " + rc.getName() + " -> " + rc.getType().getName());
        }
        System.out.println("--- KeyInput ---");
        for (RecordComponent rc : KeyInput.class.getRecordComponents()) {
            System.out.println("KeyInput: " + rc.getName() + " -> " + rc.getType().getName());
        }
        System.out.println("--- CharInput ---");
        for (RecordComponent rc : CharInput.class.getRecordComponents()) {
            System.out.println("CharInput: " + rc.getName() + " -> " + rc.getType().getName());
        }
    }
}
