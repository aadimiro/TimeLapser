import sys
import re

def write_custom_commands(outfile):
    outfile.write(";Start Patch for TimeLapse\n")
    outfile.write("G91\n")          # Use relative positioning mode
    outfile.write("G1 E-5 F1500\n")       # Retract -5mm at 25mm/s
    outfile.write("G1 Z0.3 F420\n")        # Move Z up 2mm
    outfile.write("G90\n")          # Use absolute positioning mode
    outfile.write("G1 X65 Y65 F5200\n")   # Park head and bed
    outfile.write("G91\n")          # Use relative positioning mode
    outfile.write("G1 E-2 F80\n")       # Retract -2mm at 1.333mm/s (i.e. in 1.5 s pause)
    outfile.write("G90\n")          # Use absolute positioning mode
    outfile.write(";End Patch for TimeLapse\n")


def manipulate_gcode(input_file, output_file):
    with open(input_file, 'r') as infile, open(output_file, 'w') as outfile:
        layer_number = 0
        for line in infile:
            if line.startswith(';end gcode'):
                # Insert custom commands before ;end gcode
                write_custom_commands(outfile)
            outfile.write(line)
            if line.startswith(';layer:') or line.startswith(';LAYER:'):
                if layer_number > 0:  # Skip modifications for the first layer
                    write_custom_commands(outfile)
                layer_number += 1

def main():
    if len(sys.argv) != 2:
        print("Usage: python 3delapser.py <input_gcode_file>")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = input_file.replace('.gcode', '_mod.gcode')

    manipulate_gcode(input_file, output_file)
    print(f"Modified G-code saved to: {output_file}")

if __name__ == "__main__":
    main()
