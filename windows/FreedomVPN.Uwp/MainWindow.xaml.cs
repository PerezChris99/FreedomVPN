using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using FreedomVPN.Uwp.ViewModels;

namespace FreedomVPN.Uwp;

/// <summary>
/// Main window for the FreedomVPN application
/// </summary>
public sealed partial class MainWindow : Window
{
    public MainViewModel ViewModel { get; }

    public MainWindow()
    {
        InitializeComponent();
        
        Title = "FreedomVPN";
        ViewModel = new MainViewModel();
        
        // Set window size
        var appWindow = this.AppWindow;
        appWindow.Resize(new Windows.Graphics.SizeInt32(400, 700));
    }
}
