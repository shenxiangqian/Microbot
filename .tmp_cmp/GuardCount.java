import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuardCount
{
	private static final Pattern NON_CODE = Pattern.compile(
		"(?s)/\\*.*?\\*/|(?m)//[^\\r\\n]*|\"(?:\\\\.|[^\\\\\"])*\"|'(?:\\\\.|[^\\\\'])*'");

	public static void main(String[] args) throws Exception
	{
		for (String arg : args)
		{
			Path p = Paths.get(arg);
			String source = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
			System.out.println(p.getFileName() + " -> " + methodLineCount(source,
				"private static WalkerState processWalk(WorldPoint target, int distance, int partialRetries)"));
		}
	}

	private static int methodLineCount(String source, String signature)
	{
		String code = blankNonCode(source);
		int signatureStart = code.indexOf(signature);
		if (signatureStart < 0)
		{
			throw new AssertionError("Method signature not found: " + signature);
		}
		int bodyStart = code.indexOf('{', signatureStart + signature.length());
		if (bodyStart < 0)
		{
			throw new AssertionError("Method body not found: " + signature);
		}
		int depth = 0;
		for (int i = bodyStart; i < source.length(); i++)
		{
			char c = source.charAt(i);
			if (c == '{')
			{
				depth++;
			}
			else if (c == '}' && --depth == 0)
			{
				return (int) source.substring(signatureStart, i + 1).chars().filter(ch -> ch == '\n').count() + 1;
			}
		}
		throw new AssertionError("Unclosed method body: " + signature);
	}

	private static String blankNonCode(String source)
	{
		Matcher matcher = NON_CODE.matcher(source);
		StringBuffer result = new StringBuffer(source.length());
		while (matcher.find())
		{
			StringBuilder blank = new StringBuilder(matcher.group().length());
			for (int i = 0; i < matcher.group().length(); i++)
			{
				char c = matcher.group().charAt(i);
				blank.append(c == '\n' || c == '\r' ? c : ' ');
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(blank.toString()));
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
